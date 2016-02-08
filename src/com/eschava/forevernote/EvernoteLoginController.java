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

import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetTemporaryToken;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.http.HttpTransport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;

/**
 * Evernote OAuth login controller
 *
 * @author Eugene Schava
 */
@Controller
@SessionAttributes(EvernoteLoginController.OAUTH_TOKEN_SECRET_SESSION_ATTR)
public class EvernoteLoginController
{
    static final String OAUTH_TOKEN_SECRET_SESSION_ATTR = "oauthTokenSecret";
    private static final String EVERNOTE_LOGIN_HOST = "https://www.evernote.com/";

    private HttpTransport transport;

    public void setTransport(HttpTransport transport)
    {
        this.transport = transport;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String get(ModelMap model, HttpServletRequest servletRequest) throws IOException
    {
        OAuthHmacSigner signer = new OAuthHmacSigner();
        signer.clientSharedSecret = Constants.EVERNOTE_CLIENT_SHARED_SECRET;

        String callbackUrl = new URL(new URL(servletRequest.getRequestURL().toString()), "evernote-login-confirm").toString();

        OAuthGetTemporaryToken temporaryToken = new OAuthGetTemporaryToken(EVERNOTE_LOGIN_HOST + "oauth");
        temporaryToken.callback = callbackUrl;
        temporaryToken.consumerKey = Constants.EVERNOTE_CONSUMER_KEY;
        temporaryToken.transport = transport;
        temporaryToken.signer = signer;

        OAuthCredentialsResponse response = temporaryToken.execute();
        String url = EVERNOTE_LOGIN_HOST + "OAuth.action?oauth_token=" + response.token;
        String tokenSecret = response.tokenSecret;

        model.addAttribute(OAUTH_TOKEN_SECRET_SESSION_ATTR, tokenSecret);
        return "redirect:" + url;
    }
}
