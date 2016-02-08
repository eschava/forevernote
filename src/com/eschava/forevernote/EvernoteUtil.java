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

import com.evernote.edam.type.Data;
import com.evernote.edam.type.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Evernote utility functions
 *
 * @author Eugene Schava
 */
public class EvernoteUtil
{
    public static Resource createResource(InputStream stream, String contentType) throws IOException
    {
        try
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            FileCopyUtils.copy(stream, outputStream);
            byte[] bytes = outputStream.toByteArray();

            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] hash = messageDigest.digest(bytes);

            Data data = new Data();
            data.setBody(bytes);
            data.setBodyHash(hash);

            Resource resource = new Resource();
            resource.setMime(contentType);
            resource.setData(data);

            return resource;
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e); // is not possible
        }
    }

    public static String getHash(Resource resource)
    {
        Data data = resource.getData();
        byte[] hash = data.getBodyHash();
        StringBuilder builder = new StringBuilder();
        for (byte b : hash)
        {
            String hex = Integer.toHexString(b < 0 ? 256 + b : b);
            if (hex.length() < 2)
                builder.append('0');
            builder.append(hex);
        }
        return builder.toString();
    }
}
