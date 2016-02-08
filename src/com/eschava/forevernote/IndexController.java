package com.eschava.forevernote;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpSession;

/**
 * Checks if user is logged in and shows different pages
 *
 * @author Eugene Schava
 */
@Controller
public class IndexController
{
    @RequestMapping
    public String service(HttpSession session)
    {
        boolean loggedIn = session.getAttribute(EvernoteLoginConfirmController.USER_ATTR) != null;
        return loggedIn ? "index-user" : "index";
    }
}
