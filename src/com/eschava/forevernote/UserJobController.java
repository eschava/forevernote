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
 * Pipeline job to prepare to user new notes checking
 *
 * @author Eugene Schava
 */
@Controller
public class UserJobController
{
    private static final Logger log = Logger.getLogger(UserJobController.class.getName());

    private ObjectifyFactory objectifyFactory;

    @SuppressWarnings("UnusedDeclaration")
    public void setObjectifyFactory(ObjectifyFactory objectifyFactory)
    {
        this.objectifyFactory = objectifyFactory;
    }
    
    @RequestMapping
    @ResponseBody
    public String service(@RequestParam("userId") String userId)
    {
        log.info("Checking user " + userId);

        Objectify ofy = objectifyFactory.begin();
        LoadResult<User> userRef = ofy.load().type(User.class).id(userId);
        User user = userRef.now();

        try
        {
            user.start(false);

            for (Note note : user.getNewNotes())
                user.processNote(note);
        }
        catch (Exception e)
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
