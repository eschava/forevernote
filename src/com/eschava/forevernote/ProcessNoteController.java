/*
 * Copyright (c) 1997-2012 InfoReach, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * InfoReach ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with InfoReach.
 *
 * CopyrightVersion 2.0
 *
 */
package com.eschava.forevernote;

import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.type.Note;
import com.googlecode.objectify.LoadResult;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller to process created/changed note task from task queue
 *
 * @author Eugene Schava
 */
@Controller
public class ProcessNoteController
{
    private static final Logger log = Logger.getLogger(ProcessNoteController.class.getName());

    private ObjectifyFactory objectifyFactory;

    @SuppressWarnings("UnusedDeclaration")
    public void setObjectifyFactory(ObjectifyFactory objectifyFactory)
    {
        this.objectifyFactory = objectifyFactory;
    }

    @RequestMapping
    @ResponseBody
    public String service(@RequestParam("userId") String userId, @RequestParam("noteGuid") String noteGuid)
    {
        log.info("Checking note " + noteGuid + " of user " + userId);

        Objectify ofy = objectifyFactory.begin();
        LoadResult<User> userRef = ofy.load().type(User.class).id(userId);
        User user = userRef.now();
        if (user == null)
            return "";

        try
        {
            user.start(false);

            // todo: if note was updated - ignore next processing during one minute (use memcache)
            Note note = user.getNote(noteGuid);

            if (user.isNoteShouldBeProcessed(note))
                user.processNote(note);
        }
        catch (Throwable e)
        {
            log.log(Level.WARNING, e.getMessage(), e);

            if (e instanceof EDAMUserException && ((EDAMUserException)e).getErrorCode() == EDAMErrorCode.AUTH_EXPIRED)
            {
                user.setAuthExpired(true);
            }
        }
        finally
        {
            user.finish();
        }

        // save updated user properties anyway
        ofy.save().entity(user);

        return "";
    }
}
