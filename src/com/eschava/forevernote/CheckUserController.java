package com.eschava.forevernote;

import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMUserException;
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
 * Check if user is still active
 *
 * @author Eugene Schava
 */
@Controller
public class CheckUserController
{
    private static final Logger log = Logger.getLogger(CheckUserController.class.getName());

    private ObjectifyFactory objectifyFactory;

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
            user.start(true);
        }
        catch (Exception e)
        {
            log.log(Level.WARNING, e.getMessage(), e);

            if (e instanceof EDAMUserException && ((EDAMUserException)e).getErrorCode() == EDAMErrorCode.AUTH_EXPIRED)
            {
                user.setAuthExpired(true);
                ofy.save().entity(user);
            }
        }
        finally
        {
            user.finish();
        }

        return "";
    }
}
