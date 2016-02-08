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

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

/**
 * Controller for request called by evernote webhook when some user note is created/updated
 * Adds note processing to task queue
 *
 * @author Eugene Schava
 */
@Controller
public class NoteUpdatedController
{
    private String noteJobUrl;

    public void setNoteJobUrl(String noteJobUrl)
    {
        this.noteJobUrl = noteJobUrl;
    }

    @RequestMapping
    @ResponseBody
    public String service(@RequestParam("userId") String userId, @RequestParam("guid") String noteGuid)
    {
        TaskOptions taskOptions = TaskOptions.Builder.withUrl(noteJobUrl).param("userId", userId).param("noteGuid", noteGuid);
        QueueFactory.getDefaultQueue().add(taskOptions);

        return "";
    }
}
