package com.eschava.forevernote;

import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.http.*;
import com.google.api.client.util.Key;
import com.googlecode.objectify.LoadResult;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Evernote OAuth login confirmed controller
 *
 * @author Eugene Schava
 */
@Controller
@SessionAttributes(value = {
    EvernoteLoginController.OAUTH_TOKEN_SECRET_SESSION_ATTR,
    EvernoteLoginConfirmController.USER_ATTR,
    EvernoteLoginConfirmController.NOTEBOOKS_ATTR
})
public class EvernoteLoginConfirmController
{
    private static final Logger log = Logger.getLogger(EvernoteLoginConfirmController.class.getName());

    static final String USER_ATTR = "user";
    static final String NOTEBOOKS_ATTR = "notebooks";
    private static final String EVERNOTE_LOGIN_HOST = "https://www.evernote.com";

    private HttpTransport transport;
    private ObjectifyFactory objectifyFactory;

    public void setTransport(HttpTransport transport)
    {
        this.transport = transport;
    }

    public void setObjectifyFactory(ObjectifyFactory objectifyFactory)
    {
        this.objectifyFactory = objectifyFactory;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String get(@RequestParam("oauth_token") String authToken,
                      @RequestParam("oauth_verifier") String authVerifier,
                      @ModelAttribute(EvernoteLoginController.OAUTH_TOKEN_SECRET_SESSION_ATTR) String tokenSecret,
                      ModelMap model) 
        throws Exception
    {
        OAuthHmacSigner signer = new OAuthHmacSigner();
        signer.clientSharedSecret = Constants.EVERNOTE_CLIENT_SHARED_SECRET;
        signer.tokenSharedSecret = tokenSecret;

        OAuthGetAccessToken accessToken = new OAuthGetAccessToken(EVERNOTE_LOGIN_HOST + "/oauth");
        accessToken.temporaryToken = authToken;
        accessToken.verifier = authVerifier;
        accessToken.consumerKey = Constants.EVERNOTE_CONSUMER_KEY;
        accessToken.transport = transport;
        accessToken.signer = signer;

//        OAuthCredentialsResponse credentialsResponse = accessToken.execute();
        OAuthCredentialsResponse credentialsResponse = execute(accessToken);
        String userId = credentialsResponse.evernoteUserId;

        Objectify ofy = objectifyFactory.begin();
        LoadResult<User> userRef = ofy.load().type(User.class).id(userId);
        User user = userRef.now();
        if (user == null)
            user = new User(userId);

        try
        {
            Map<String, String> notebooks = user.initialize(credentialsResponse.token, credentialsResponse.evernoteShardId);

            ofy.save().entity(user);

            model.addAttribute(USER_ATTR, user);
            model.addAttribute(NOTEBOOKS_ATTR, notebooks);
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }

        return "closePopupAndReloadParent";
    }

    // copied from com.google.api.client.auth.oauth.AbstractOAuthGetToken.execute()
    private OAuthCredentialsResponse execute(OAuthGetAccessToken accessToken) throws IOException
    {
        final boolean usePost = false;
        HttpRequestFactory requestFactory = transport.createRequestFactory();
        HttpRequest request =
            requestFactory.buildRequest(usePost ? HttpMethod.POST : HttpMethod.GET, accessToken, null);
        accessToken.createParameters().intercept(request);
        HttpResponse response = request.execute();
        response.setDisableContentLogging(true);
        OAuthCredentialsResponse oauthResponse = new OAuthCredentialsResponse();
        UrlEncodedParser.parse(response.parseAsString(), oauthResponse);
        return oauthResponse;
    }
    
    public final class OAuthCredentialsResponse 
    {
        @Key("oauth_token")
        public String token;
        @Key("oauth_token_secret")
        public String tokenSecret;
        @Key("oauth_callback_confirmed")
        public Boolean callbackConfirmed;
        @Key("edam_shard")
        public String evernoteShardId;
        @Key("edam_userId")
        public String evernoteUserId;
    }
}
