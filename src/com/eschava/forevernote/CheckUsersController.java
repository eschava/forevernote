package com.eschava.forevernote;

import java.util.logging.Logger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;

/**
 * Check if users are still active
 *
 * @author Eugene Schava
 */
@Controller
public class CheckUsersController
{
    private static final Logger log = Logger.getLogger(CheckUsersController.class.getName());

    private ObjectifyFactory objectifyFactory;
    private String userJobUrl;

    public void setObjectifyFactory(ObjectifyFactory objectifyFactory)
    {
        this.objectifyFactory = objectifyFactory;
    }

    public void setUserJobUrl(String userJobUrl)
    {
        this.userJobUrl = userJobUrl;
    }

    @RequestMapping
    @ResponseBody
    public String service()
    {
        log.info("Checking users");

        Objectify ofy = objectifyFactory.begin();
        Iterable<User> users = ofy.load().type(User.class).iterable();
        for (User user : users)
        {
            if (!user.isAuthExpired())
            {
                TaskOptions taskOptions = TaskOptions.Builder.withUrl(userJobUrl).param("userId", user.getUserId());
                QueueFactory.getDefaultQueue().add(taskOptions);
            }
        }

        return "";
    }
}
