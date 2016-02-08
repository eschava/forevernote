package com.eschava.forevernote.logging;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.*;

/**
 * Filter to add {@link GAEEmailLogHandler} to logger
 * Idea taken from http://code.google.com/p/gae-xmpp-logger/
 *
 * @author Eugene Schava
 */
public class GAEEmailLogFilter implements Filter
{
    private static final Logger log = Logger.getLogger(GAEEmailLogFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        log.getParent().addHandler(new GAEEmailLogHandler());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {
    }
}
