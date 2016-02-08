package com.eschava.forevernote;

import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.limits.Constants;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.notestore.NoteStoreIface;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteAttributes;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;
import com.evernote.edam.userstore.UserStore;
import com.evernote.thrift.TException;
import com.evernote.thrift.protocol.TBinaryProtocol;
import com.evernote.thrift.transport.THttpClient;
import com.evernote.thrift.transport.TTransportException;
import com.google.api.client.http.HttpResponseException;
import com.google.appengine.api.urlfetch.ResponseTooLargeException;
import com.google.apphosting.api.DeadlineExceededException;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;
import org.jdom.input.DOMBuilder;
import org.jdom.output.XMLOutputter;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.eschava.forevernote.Constants.*;

/**
 * Information about evernote user
 *
 * @author Eugene Schava
 */
@Entity
public class User implements Serializable
{
    private static final long serialVersionUID = 7381529685357933454L;

    private static final Logger log = Logger.getLogger(User.class.getName());

    private static final String EVERNOTE_NOTE_API_HOST = "https://www.evernote.com";
    private static final DateFormat TIME_FILTER_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'"); // 20070704T150000Z in GMT

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    static
    {
        TIME_FILTER_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        try
        {
            DOCUMENT_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DOCUMENT_BUILDER_FACTORY.setExpandEntityReferences(false);
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private transient @Ignore NoteStoreIface noteStore;

    private @Id String userId;
    private String username;
    private String email;
    private Set<String> notebooks;
    private boolean notebooksToIgnore;

    private String authenticationToken;
    private String shardId;
    private boolean authExpired;

    private long lastCheck;
    private transient @Ignore long lastCheckStarted;

    private String webSitesNotebookGuid;
    private String ignoteTagGuid;
    private String reloadTagGuid;
    private String pageTagGuid;

    private User()
    {
    }

    public User(String userId)
    {
        this.userId = userId;
        this.lastCheck = System.currentTimeMillis(); // check only notes updated after user is registered
    }

    public Map<String, String> initialize(String token, String shardId) throws Exception
    {
        this.authenticationToken = token;
        this.shardId = shardId;
        this.authExpired = false;

        try
        {
            start(true);

            // TODO: refactor and do not list notebooks twice
            List<Notebook> allNotebooks = getClient().listNotebooks(authenticationToken);
            Map<String, String> allNotebooksMap = new LinkedHashMap<String, String>(allNotebooks.size());
            for (Notebook notebook : allNotebooks)
                allNotebooksMap.put(notebook.getGuid(), notebook.getName());

            if (notebooks == null)
            {
                notebooks = new HashSet<String>();
                notebooks.add(webSitesNotebookGuid);
                notebooksToIgnore = true;
            }

            return allNotebooksMap;
        }
        finally
        {
            finish();
        }
    }

    public void start(boolean force) throws Exception
    {
        getClient(); // enforce connection

        webSitesNotebookGuid = checkIfNotebookExists(force, WEB_NOTEBOOK, webSitesNotebookGuid);
        String[] tagGuids = checkIfTagsExist(force, IGNORE_TAG, ignoteTagGuid, RELOAD_TAG, reloadTagGuid, PAGE_TAG, pageTagGuid);
        ignoteTagGuid = tagGuids[0];
        reloadTagGuid = tagGuids[1];
        pageTagGuid = tagGuids[2];

        if (force)
        {
            THttpClient httpClient = new THttpClient(EVERNOTE_NOTE_API_HOST + "/edam/user");
            TBinaryProtocol protocol = new TBinaryProtocol(httpClient);
            UserStore.Client userStore = new UserStore.Client(protocol);

            com.evernote.edam.type.User user = userStore.getUser(authenticationToken);
            username = user.getUsername();
            if (email == null)
                email = user.getEmail();

            httpClient.close();
        }
    }

    public boolean isNoteShouldBeProcessed(Note note)
    {
        String source = note.getAttributes().getSource();
        if (source != null && (source.equals(com.evernote.edam.type.Constants.EDAM_NOTE_SOURCE_WEB_CLIP) || source.equals("Clearly")))
            return false;

        List<String> tagGuids = note.getTagGuids();
        if (tagGuids != null && tagGuids.contains(pageTagGuid))
            return false;

        String notebookGuid = note.getNotebookGuid();
        boolean notebookListed = notebooks.contains(notebookGuid);
        return notebooksToIgnore ? !notebookListed : notebookListed;
    }

    public void processNote(Note note) throws EDAMUserException, TException, EDAMSystemException, EDAMNotFoundException, IOException, SAXException, ParserConfigurationException, TransformerException
    {
        NoteAttributes attributes = note.getAttributes();
        log.info("Note source: " + attributes.getSource() + "/" + attributes.getSourceApplication());

        boolean updated = false;

        List<String> tagGuids = note.getTagGuids();
        boolean reload = tagGuids != null && tagGuids.contains(getReloadTagGuid());
        boolean ignore = tagGuids != null && tagGuids.contains(getIgnoteTagGuid());
        String noteContent = note.getContent();

        // reload means all links resolved before should be reloaded
        if (reload)
        {
            try
            {
                if (noteContent == null)
                    noteContent = getNoteContent(note);
                List<String> webNoteGuids = getWebNoteGuids(noteContent);
                if (!webNoteGuids.isEmpty())
                    removeNotes(webNoteGuids);
            }
            catch (Exception e)
            {
                log.log(Level.WARNING, e.getMessage(), e);
            }

            tagGuids.remove(getReloadTagGuid());
            note.setTagGuids(tagGuids);

            updated = true;
        }

        Map<String, String> newNoteGuids = new HashMap<String, String>();
        Map<String, String> newNoteErrors = new HashMap<String, String>();
        // it's OK to have both ignore and reload flags
        if (!ignore)
        {
            if (noteContent == null)
                noteContent = getNoteContent(note);

            for (String link : getNoteLinks(note, noteContent, reload))
            {
                try
                {
                    Note newNote = new FullPageService().getPageContent(link);
                    if (newNote != null)
                    {
                        String guid = createNewNote(newNote, link, note);
                        newNoteGuids.put(link, guid);
                        updated = true;
                    }
                }
                catch (ResponseTooLargeException e)
                {
                    // if GAE says response too large - ignore it. Likely it's binary file
                    log.log(Level.INFO, e.getMessage(), e);
                }
                catch (HttpResponseException e)
                {
                    newNoteErrors.put(link, String.valueOf(e.getStatusCode()));
                    updated = true;

                    String msg = "Error " + e.getStatusCode() + " during retrieving " + link; // not exception message because it could contain full page content
//                    Exception copy = new Exception();
//                    copy.setStackTrace(e.getStackTrace());
                    log.log(Level.INFO, msg);
                }
                catch (EDAMUserException e)
                {
                    if (e.getErrorCode() == EDAMErrorCode.QUOTA_REACHED)
                        log.log(Level.INFO, e.getMessage(), e); // it's not my problem
                    else
                        log.log(Level.WARNING, e.getMessage(), e);
                }
                catch (DeadlineExceededException e)
                {
                    log.log(Level.INFO, e.getMessage(), e); // it's not my problem
                }
                catch (Throwable e)
                {
                    log.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }

        if (updated)
        {
            updateNote(note, noteContent, newNoteGuids, newNoteErrors, reload);
        }
    }

    public void finish()
    {
        if (lastCheckStarted != 0)
            lastCheck = lastCheckStarted;

        if (noteStore != null)
            ((NoteStore.Client)noteStore).getInputProtocol().getTransport().close();
    }

    public List<Note> getNewNotes() throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException
    {
        String lastCheckFilter;
        synchronized (TIME_FILTER_FORMAT)
        {
            lastCheckFilter = TIME_FILTER_FORMAT.format(lastCheck);
        }

        log.info("Processing user " + userId + " new notes from " + lastCheckFilter);

        List<Note> list = new LinkedList<Note>();

        lastCheckStarted = System.currentTimeMillis();
        NoteFilter filter = new NoteFilter();
        filter.setWords("updated:" + lastCheckFilter + " AND -tag:" + PAGE_TAG);
        NoteList noteList = getClient().findNotes(authenticationToken, filter, 0, Constants.EDAM_USER_NOTES_MAX);

        for (Note note : noteList.getNotes())
            if (isNoteShouldBeProcessed(note))
                list.add(note);
        return list;
    }

    public Note getNote(String noteGuid) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException
    {
        return getClient().getNote(authenticationToken, noteGuid, true/*with content*/, false, false, false);
    }

    public String getNoteContent(Note note) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException
    {
        log.info("Retrieving content of note '" + note.getTitle() + "' of user " + userId);
        return getClient().getNoteContent(authenticationToken, note.getGuid());
    }

    public List<String> getWebNoteGuids(String content) throws IOException, SAXException, ParserConfigurationException
    {
        DocumentBuilder docBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        Document doc = docBuilder.parse(new InputSource(new StringReader(content)));

        NodeList linksList = doc.getElementsByTagName("a");
        List<String> result = new ArrayList<String>();

        for (int i = 0, count = linksList.getLength(); i < count; i++)
        {
            Node link = linksList.item(i);
            Attr hrefAttr = (Attr) link.getAttributes().getNamedItem("href");
            if (hrefAttr == null)
                continue;
            String href = unescape(hrefAttr.getValue());

            // skip local links
            if (isLocalLink(href))
                continue;

            if (IconifyNoteService.linkHasPageIcon(link, href))
            {
                String webNoteLink = IconifyNoteService.getPageIconUrl(link);
                String[] parts = webNoteLink.split("/");
                String webNoteGuid = parts[parts.length - 2];

                result.add(webNoteGuid);
            }
        }
        return result;
    }

    public Collection<String> getNoteLinks(Note note, String content, boolean all) throws ParserConfigurationException, IOException, SAXException
    {
        log.info("Processing note '" + note.getTitle() + "' of user " + userId);

        DocumentBuilder docBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        Document doc = docBuilder.parse(new InputSource(new StringReader(content)));

        NodeList linksList = doc.getElementsByTagName("a");
        Set<String> result = new HashSet<String>();

        for (int i = 0, count = linksList.getLength(); i < count; i++)
        {
            Node link = linksList.item(i);
            Attr hrefAttr = (Attr) link.getAttributes().getNamedItem("href");
            if (hrefAttr == null)
                continue;
            String href = unescape(hrefAttr.getValue());

            // skip local links
            if (isLocalLink(href))
                continue;

            if (isIgnoredLink(href))
                continue;

            if (all || !IconifyNoteService.linkHasIcon(link, href))
            {
                result.add(href);
            }
        }

        return result;
    }

    public String createNewNote(Note webSiteNote, String sourceUrl, Note parentNote) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException
    {
        try
        {
            log.info("Creating new note with title '" + webSiteNote.getTitle() +
                "' for URL '" + sourceUrl +
                "' for user " + userId);

            NoteAttributes noteAttributes = new NoteAttributes();

            noteAttributes.setSourceURL(sourceUrl);
            noteAttributes.setSource("Forevernote");
            noteAttributes.setSourceApplication(parentNote.getGuid());

            webSiteNote.setAttributes(noteAttributes);
            webSiteNote.setTagGuids(Collections.singletonList(pageTagGuid));
            webSiteNote.setNotebookGuid(webSitesNotebookGuid);
            webSiteNote = getClient().createNote(authenticationToken, webSiteNote);

            return webSiteNote.getGuid();
        }
        catch (EDAMNotFoundException e)
        {
            if (e.getIdentifier().equals("Note.notebookGuid"))
            {
                log.log(Level.WARNING, "Notebook with id=" + e.getKey() + " (web-notebook key=" + webSitesNotebookGuid + ") doesn't exist. Recreating", e);
                // if notebook doesn't exist - recreate it
                webSitesNotebookGuid = checkIfNotebookExists(true, WEB_NOTEBOOK, webSitesNotebookGuid);
                return createNewNote(webSiteNote, sourceUrl, parentNote); // Warning!!! recursive call
            }
            throw e;
        }
    }

    public void updateNote(Note note, String noteContent,
                           Map<String, String> linkToNoteGuidMap, Map<String, String> linkToErrorMap,
                           boolean all)
        throws ParserConfigurationException, TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException, TransformerException, IOException, SAXException
    {
        log.info("Updating content of note '" + note.getTitle() + "' of user " + userId);
        // parse document again to do not pass DOM entities between jobs
        DocumentBuilder docBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        Document doc = docBuilder.parse(new InputSource(new StringReader(noteContent)));

        NodeList linksList = doc.getElementsByTagName("a");
        List<Node> links = new ArrayList<Node>(linksList.getLength());
        for (int i = 0, count = linksList.getLength(); i < count; i++)
            links.add(linksList.item(i));
        boolean pageIconAdded = false;
        boolean errorIconAdded = false;

        for (Node link : links)
        {
            Attr hrefAttr = (Attr) link.getAttributes().getNamedItem("href");
            if (hrefAttr == null)
                continue;
            String href = unescape(hrefAttr.getValue());

            // skip local links
            if (isLocalLink(href))
                continue;

            String webSiteNoteGuid = linkToNoteGuidMap.get(href);
            String error = linkToErrorMap.get(href);
            if (webSiteNoteGuid != null)
            {
                String webSiteNoteHref = "evernote:///view/" + userId + "/" + shardId + "/" + webSiteNoteGuid + "/" + webSiteNoteGuid + "/";

                IconifyNoteService.addLinkPageIcon(doc, link, href, webSiteNoteHref);
                pageIconAdded = true;
            }
            else if (error != null)
            {
                IconifyNoteService.addLinkErrorIcon(doc, link, href, error);
                errorIconAdded = true;
            }
            else if (all & IconifyNoteService.linkHasIcon(link, href))
            {
                IconifyNoteService.removeIcon(link);
            }
        }

        IconifyNoteService.finalizeNote(note, pageIconAdded, errorIconAdded);

//        Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
//        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://xml.evernote.com/pub/enml2.dtd");
//        StreamResult result = new StreamResult(new StringWriter());
//        DOMSource source = new DOMSource(doc);
//        transformer.transform(source, result);
//        String content = result.getWriter().toString();

        // Use JDom XML serializer instead of built-in to do not lost XML entities in text
        StringWriter stringWriter = new StringWriter();
        DOMBuilder domBuilder = new DOMBuilder();
        new XMLOutputter().output(domBuilder.build(doc), stringWriter);
        String content = stringWriter.toString();

        note.setContent(content);

        getClient().updateNote(authenticationToken, note);
    }

    public void removeNotes(List<String> guids) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException
    {
        for (String guid : guids)
            getClient().deleteNote(authenticationToken, guid);
    }

    private String checkIfNotebookExists(boolean force, String name, String guid) throws TException, EDAMSystemException, EDAMUserException
    {
        if (!force && guid != null) return guid;

        NoteStoreIface client = getClient();
        List<Notebook> notebooks = client.listNotebooks(authenticationToken);

        int n = 0;
        // dummy workaround for EDAMUserException(errorCode:DATA_CONFLICT, parameter:Notebook.name)
        while(true)
        {
            try
            {
                String notebookName = name + (n > 0 ? n : "");

                for (Notebook notebook : notebooks)
                {
                    if (notebook.getName().equals(notebookName))
                        return notebook.getGuid();
                }

                Notebook notebook = new Notebook();
                notebook.setName(notebookName);
                log.info("Creating notebook with name " + notebookName);
                notebook = client.createNotebook(authenticationToken, notebook);
                return notebook.getGuid();
            }
            catch (EDAMUserException e)
            {
                if (e.getErrorCode() == EDAMErrorCode.DATA_CONFLICT)
                {
                    log.log(Level.WARNING, e.getMessage(), e);
                    n++;
                }
            }
        }
    }

    private String[] checkIfTagsExist(boolean force, String... namesAndGuids) throws EDAMUserException, TException, EDAMSystemException, EDAMNotFoundException
    {
        int count = namesAndGuids.length / 2;
        String[] result = new String[count];

        if (!force)
        {
            boolean emptyArePresent = false;
            for (int i = 0; i < count; i++)
            {
                result[i] = namesAndGuids[i*2 + 1];
                if (result[i] == null)
                    emptyArePresent = true;
            }

            if (!emptyArePresent)
                return result;
        }

        NoteStoreIface client = getClient();
        List<Tag> tags = client.listTags(authenticationToken);

        for (int i = 0; i < count; i++)
        {
            if (result[i] == null)
            {
                String name = namesAndGuids[i * 2];
                for (Tag tag : tags)
                {
                    if (tag.getName().equals(name))
                    {
                        result[i] = tag.getGuid();
                        break;
                    }
                }

                if (result[i] == null)
                {
                    Tag tag = new Tag();
                    tag.setName(name);
                    log.info("Creating tag with name " + name);
                    tag = client.createTag(authenticationToken, tag);

                    result[i] = tag.getGuid();
                }
            }
        }

        return result;
    }

    public String getUserId()
    {
        return userId;
    }

    public String getUsername()
    {
        return username;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getEmail()
    {
        return email;
    }

    public Set<String> getNotebooks()
    {
        return notebooks;
    }

    public void setNotebooks(Set<String> notebooks)
    {
        this.notebooks = notebooks;
    }

    public boolean isNotebooksToIgnore()
    {
        return notebooksToIgnore;
    }

    public void setNotebooksToIgnore(boolean notebooksToIgnore)
    {
        this.notebooksToIgnore = notebooksToIgnore;
    }

    private NoteStoreIface getClient() throws TTransportException
    {
        if (noteStore == null)
        {
            THttpClient httpClient = new THttpClient(EVERNOTE_NOTE_API_HOST + "/edam/note/" + shardId);
            TBinaryProtocol protocol = new TBinaryProtocol(httpClient);
            noteStore = new NoteStore.Client(protocol);
        }

        return noteStore;
    }

    public void setAuthExpired(boolean authExpired)
    {
        this.authExpired = authExpired;
    }

    public boolean isAuthExpired()
    {
        return authExpired;
    }

    public String getIgnoteTagGuid()
    {
        return ignoteTagGuid;
    }

    public String getReloadTagGuid()
    {
        return reloadTagGuid;
    }

    private static boolean isLocalLink(String href)
    {
        return href.startsWith("evernote:");
    }

    private static boolean isIgnoredLink(String href)
    {
        try
        {
            URL url = new URL(href);

            String protocol = url.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https"))
                return true;

            String path = url.getPath();
            return path.isEmpty() || path.equals("/");
        }
        catch (MalformedURLException e)
        {
            return true;
        }
    }

    static String unescape(String value)
    {
        if (value == null) return null;

        return value.replace("&amp;", "&");
    }
}
