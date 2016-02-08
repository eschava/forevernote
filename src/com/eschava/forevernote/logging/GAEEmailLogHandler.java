package com.eschava.forevernote.logging;

import com.eschava.forevernote.Constants;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Log handler to send any important log message to GAE admin
 *
 * @author Eugene Schava
 */
public class GAEEmailLogHandler extends Handler
{
    private static final int LEVEL = Level.WARNING.intValue();
    private static final int MAX_LENGTH = 10000;

    public GAEEmailLogHandler()
    {
    }

    @Override
    public void publish(LogRecord record)
    {
        if (record.getLevel().intValue() >= LEVEL)
        {
            try
            {
                MailService.Message msg = new MailService.Message();
                msg.setSender(Constants.WARNING_EMAIL_SENDER);
                msg.setSubject(Constants.WARNING_EMAIL_SUBJECT);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.append("Error at ").append(record.getSourceClassName()).append(" ").append(record.getSourceMethodName()).append(": ").append(record.getMessage());

                @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                Throwable exception = record.getThrown();
                if (exception != null)
                {
                    printStream.append("\n").append(exception.getMessage()).append("\n");
                    exception.printStackTrace(printStream);
                }

                String body = outputStream.toString();
                if (body.length() > MAX_LENGTH)
                    body = body.substring(0, MAX_LENGTH);
                msg.setTextBody(body);

                MailService mailService = MailServiceFactory.getMailService();
                mailService.sendToAdmins(msg);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close() throws SecurityException
    {
    }
}
