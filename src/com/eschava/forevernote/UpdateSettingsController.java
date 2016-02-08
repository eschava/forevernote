package com.eschava.forevernote;

import java.util.Set;
import java.util.logging.Logger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;

/**
 * Controller to update user information
 *
 * @author Eugene Schava
 */
@Controller
@SessionAttributes(EvernoteLoginConfirmController.USER_ATTR)
public class UpdateSettingsController
{
    private static final Logger log = Logger.getLogger(UpdateSettingsController.class.getName());

    private ObjectifyFactory objectifyFactory;

    @SuppressWarnings("UnusedDeclaration")
    public void setObjectifyFactory(ObjectifyFactory objectifyFactory)
    {
        this.objectifyFactory = objectifyFactory;
    }

    @RequestMapping(method = RequestMethod.POST)
    public String update(
        @ModelAttribute(EvernoteLoginConfirmController.USER_ATTR) User user,
        @RequestParam("email") String email,
        @RequestParam("notebooks") Set<String> notebooks,
        @RequestParam(value = "ignoreNotebooks", required = false) boolean ignoreNotebooks
    )
    {
        Objectify ofy = objectifyFactory.begin();

        if (!email.isEmpty())
            user.setEmail(email);
        user.setNotebooks(notebooks);
        user.setNotebooksToIgnore(ignoreNotebooks);

        ofy.save().entity(user);

        return "redirect:/";
    }
}
