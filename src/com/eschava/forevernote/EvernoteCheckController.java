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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

/**
 * Start evernote checking routines controller
 *
 * @author Eugene Schava
 */
@Controller
@SessionAttributes(EvernoteLoginConfirmController.USER_ATTR)
public class EvernoteCheckController
{
    private String userJobUrl;

    public void setUserJobUrl(String userJobUrl)
    {
        this.userJobUrl = userJobUrl;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String get(@ModelAttribute(EvernoteLoginConfirmController.USER_ATTR) User user)
    {
        TaskOptions taskOptions = TaskOptions.Builder.withUrl(userJobUrl).param("userId", user.getUserId());
        QueueFactory.getDefaultQueue().add(taskOptions);

        return "closePopup";
    }
}
