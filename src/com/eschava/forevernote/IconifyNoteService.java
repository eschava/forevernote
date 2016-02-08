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

import com.evernote.edam.type.Note;
import com.evernote.edam.type.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Methods related to modifing links in source note
 *
 * @author Eugene Schava
 */
public class IconifyNoteService
{
    private static final Logger log = Logger.getLogger(IconifyNoteService.class.getName());

    private static Resource LINK_IMAGE_RESOURCE;
    private static String LINK_IMAGE_HASH_HEX;
    private static final String LINK_IMAGE_MIME = "image/png";

    private static Resource LINK_IMAGE_ERROR_RESOURCE;
    private static String LINK_IMAGE_ERROR_HASH_HEX;
    private static final String LINK_IMAGE_ERROR_MIME = "image/png";

    static
    {
        try
        {
            // read link image and process it
            InputStream stream = IconifyNoteService.class.getResourceAsStream("icon.png");
            LINK_IMAGE_RESOURCE = EvernoteUtil.createResource(stream, LINK_IMAGE_MIME);
            LINK_IMAGE_HASH_HEX = EvernoteUtil.getHash(LINK_IMAGE_RESOURCE);

            // read link error image and process it
            stream = IconifyNoteService.class.getResourceAsStream("error.png");
            LINK_IMAGE_ERROR_RESOURCE = EvernoteUtil.createResource(stream, LINK_IMAGE_ERROR_MIME);
            LINK_IMAGE_ERROR_HASH_HEX = EvernoteUtil.getHash(LINK_IMAGE_ERROR_RESOURCE);
        }
        catch (IOException e)
        {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public static boolean linkHasIcon(Node link, String url)
    {
        return linkHasPageIcon(link, url) || linkHasErrorIcon(link, url);
    }

    public static boolean linkHasPageIcon(Node link, String url)
    {
        Element pageIcon = findPageIcon(link);
        String rel = getRel(url);
        return pageIcon != null && rel.equals(unescape(pageIcon.getAttribute("rel")));
    }

    public static boolean linkHasErrorIcon(Node link, String url)
    {
        Element errorIcon = findErrorIcon(link);
        String longdesc = getRel(url);
        return errorIcon != null && longdesc.equals(unescape(errorIcon.getAttribute("longdesc")));
    }

    public static String getPageIconUrl(Node link)
    {
        Element pageIcon = findPageIcon(link);
        return pageIcon.getAttribute("href");
    }

    public static void addLinkPageIcon(Document doc, Node link, String url, String pageUrl/*, String title*/)
    {
        Element pageIcon = findPageIcon(link);
        if (pageIcon == null)
        {
            Element errorIcon = findErrorIcon(link);
            if (errorIcon != null)
                removeIconImpl(errorIcon);

            pageIcon = doc.createElement("a");
            Node parentNode = link.getParentNode();
            parentNode.insertBefore(pageIcon, link.getNextSibling());

            Element image = doc.createElement("en-media");
            image.setAttribute("hash", LINK_IMAGE_HASH_HEX);
//            image.setAttribute("style", "cursor: hand;"); // doesn't work for some reason
            image.setAttribute("type", LINK_IMAGE_MIME);
            pageIcon.appendChild(image);
        }

        pageIcon.setAttribute("href", pageUrl);
//        pageIcon.setAttribute("title", title); // TODO: link or image?
        pageIcon.setAttribute("rel", getRel(url));
    }

    public static void addLinkErrorIcon(Document doc, Node link, String url, String error)
    {
        Element errorIcon = findErrorIcon(link);
        if (errorIcon == null)
        {
            Element pageIcon = findPageIcon(link);
            if (pageIcon != null)
                removeIconImpl(pageIcon);

            errorIcon = doc.createElement("en-media");
            errorIcon.setAttribute("hash", LINK_IMAGE_ERROR_HASH_HEX);
            errorIcon.setAttribute("type", LINK_IMAGE_ERROR_MIME);

            Node parentNode = link.getParentNode();
            parentNode.insertBefore(errorIcon, link.getNextSibling());
        }

        errorIcon.setAttribute("title", error);
        errorIcon.setAttribute("longdesc", getRel(url));
    }

    public static void removeIcon(Node link)
    {
        Node next = getNextElement(link);

        if (isPageIcon(next) || isErrorIcon(next))
            removeIconImpl(next);
    }

    private static void removeIconImpl(Node next)
    {
        Node previous = next.getPreviousSibling();

        next.getParentNode().removeChild(next);
        if (previous != null && isWhiteSpace(previous))
            previous.getParentNode().removeChild(previous);
    }

    public static void finalizeNote(Note note, boolean pageIconAdded, boolean errorIconAdded)
    {
        if (pageIconAdded)
            addResourceIfNeeded(note, LINK_IMAGE_RESOURCE);
        if (errorIconAdded)
            addResourceIfNeeded(note, LINK_IMAGE_ERROR_RESOURCE);
    }

    private static Element findPageIcon(Node link)
    {
        Node next = getNextElement(link);
        return next != null && isPageIcon(next) ? (Element)next : null;
    }

    private static Element findErrorIcon(Node link)
    {
        Node next = getNextElement(link);
        return next != null && isErrorIcon(next) ? (Element)next : null;
    }

    private static Element getNextElement(Node link)
    {
        Node next = link.getNextSibling();
        if (next == null)
            return null;

        // skip empty text node right after link
        if (isWhiteSpace(next))
            next = next.getNextSibling();

        if (next == null)
            return null;

        if (next instanceof Element)
            return (Element) next;

        return null;
    }

    private static boolean isPageIcon(Node node)
    {
        if (node.getNodeType() == Node.ELEMENT_NODE)
        {
            Element element = (Element) node;
            if (element.getTagName().equalsIgnoreCase("a") && isRel(element.getAttribute("rel")))
                return true;
        }

        return false;
    }

    private static boolean isErrorIcon(Node node)
    {
        if (node.getNodeType() == Node.ELEMENT_NODE)
        {
            Element element = (Element) node;

            if (element.getTagName().equalsIgnoreCase("en-media") && isRel(element.getAttribute("longdesc")))
                return true;
        }

        return false;
    }

    private static boolean isWhiteSpace(Node node)
    {
        return node.getNodeType() == Node.TEXT_NODE && ((Text) node).getWholeText().matches("[\\s\\u00A0]+");
    }

    private static String getRel(String url)
    {
        return "web:" + url;
    }

    private static boolean isRel(String attr)
    {
        return attr != null && attr.startsWith("web:");
    }

    private static String unescape(String value)
    {
        if (value == null) return null;

        return value.replace("&amp;", "&");
    }

    private static void addResourceIfNeeded(Note note, Resource resourceToAdd)
    {
        byte[] hash = resourceToAdd.getData().getBodyHash();
        List<Resource> resources = note.getResources();
        if (resources != null && !resources.isEmpty())
        {
            for (Resource resource : resources)
                if (Arrays.equals(resource.getData().getBodyHash(), hash))
                    return;
        }

        // resource is not present
        note.addToResources(resourceToAdd);
    }
}
