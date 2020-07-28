package com.eschava.forevernote;

import com.evernote.edam.limits.Constants;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.ResourceAttributes;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.wutka.dtd.DTDElement;
import com.wutka.dtd.DTDParser;
import org.htmlcleaner.BaseToken;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.springframework.web.util.HtmlUtils;

import javax.annotation.Nullable;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieves content of web-page by URL in Evernote ENML format
 *
 * @author Eugene Schava
 */
public class FullPageService
{
    private static final Logger log = Logger.getLogger(FullPageService.class.getName());

    private static final String DEFAULT_CHARSET = "cp1251"; // I don't know what is better ("file.enconding" doesn't work)
    private static final String EVERNOTE_DTD = "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">";
    private static final String EVERNOTE_PROLOGUE =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
        EVERNOTE_DTD +
        "<en-note>";
    private static final String EVERNOTE_EPILOGUE =
        "</en-note>";

    private static final Set<String> MEDIA_CONTENT_TYPES = new HashSet<String>(Constants.EDAM_MIME_TYPES);
    private static final Set<String> HTML_CONTENT_TYPES = new HashSet<String>();
    private static final int TITLE_LEN_MAX = Constants.EDAM_NOTE_TITLE_LEN_MAX - 4; // 4 chars for ellipse

    static
    {
        HTML_CONTENT_TYPES.add("text/html");
//        HTML_CONTENT_TYPES.add("text/xml"); http://blogs.technet.com/b/rutechnews/rss.aspx - looks weird
        HTML_CONTENT_TYPES.add("text/plain");
    }

    public Note getPageContent(String url) throws IOException
    {
        log.info("Retriving URL " + url);

        HttpTransport transport = new NetHttpTransport();
        GenericUrl url1 = new GenericUrl(url);
        url1.setFragment(null); // some HTTP servers return 404 for urls with fragments
        HttpRequest request = transport.createRequestFactory().buildGetRequest(url1);
        // TODO: check long data
        HttpResponse response = execute(request);
        InputStream content = response.getContent();
        @Nullable String contentType = response.getContentType();
        @Nullable String contentTypeRaw = getContentTypeRaw(contentType);

        log.info("content type " + contentType + " (" + contentTypeRaw + ")");

        if (MEDIA_CONTENT_TYPES.contains(contentTypeRaw))
            return createMediaNote(url, content, contentType);

        // TODO: format plain text
        if (HTML_CONTENT_TYPES.contains(contentTypeRaw))
            return createWebNote(url, content, contentType);

        return null;
    }

    private HttpResponse execute(HttpRequest request) throws IOException
    {
        // workaround for NPE at GenericUrl.java:104
        request.setFollowRedirects(false);
        HttpResponse response;
        try
        {
            response = request.execute();
        }
        catch (HttpResponseException e)
        {
            if (!isRedirected(e))
                throw e;

            String redirectLocation = e.getHeaders().getLocation();
            GenericUrl redirectUrl;
            if (redirectLocation.startsWith("/"))
            {
                redirectUrl = new GenericUrl();
                redirectUrl.setScheme(request.getUrl().getScheme());
                redirectUrl.setHost(request.getUrl().getHost());
                redirectUrl.setRawPath(redirectLocation);
            }
            else
                redirectUrl = new GenericUrl(redirectLocation);

            request.setUrl(redirectUrl);
            response = execute(request);
        }

        return response;
    }


    private boolean isRedirected(HttpResponseException exception) {
        int statusCode = exception.getStatusCode();
        switch (statusCode) {
          case HttpStatusCodes.STATUS_CODE_MOVED_PERMANENTLY: // 301
          case HttpStatusCodes.STATUS_CODE_FOUND: // 302
          case HttpStatusCodes.STATUS_CODE_SEE_OTHER: // 303
          case HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT: // 307
            // Redirect requests must have a location header specified.
            return exception.getHeaders().getLocation() != null;
          default:
            return false;
        }
      }

    private Note createMediaNote(String url, InputStream content, String contentTypeRaw) throws IOException
    {
        Resource resource = EvernoteUtil.createResource(content, contentTypeRaw);
        ResourceAttributes attributes = new ResourceAttributes();
        attributes.setSourceURL(url);
        resource.setAttributes(attributes);

        Note mediaNote = new Note();
        mediaNote.setTitle(url);
        mediaNote.setContent(
            EVERNOTE_PROLOGUE +
            "<en-media type=\"" + contentTypeRaw + "\" hash=\"" + EvernoteUtil.getHash(resource) + "\"/>" +
            EVERNOTE_EPILOGUE);

        mediaNote.addToResources(resource);

        return mediaNote;
    }

    public Note createWebNote(String url, InputStream content, String contentType) throws IOException
    {
        String charset = getCharset(contentType);
        if (charset == null) charset = DEFAULT_CHARSET;

        String[] pageContent = processContent(content, charset, url);

        Note webNote = new Note();
        webNote.setTitle(pageContent[0]);
        webNote.setContent(pageContent[1]);

        return webNote;
    }

    private String[] processContent(InputStream stream, String encoding, String url) throws IOException
    {
        HtmlCleaner cleaner = new HtmlCleaner();
        TagNode node = cleaner.clean(stream, encoding);

        HTMLToEvernoteVisitor visitor = new HTMLToEvernoteVisitor(new URL(url));
        visitor.visitDocument(node);

        String title = visitor.getTitle().replaceAll("\\p{Cc}", ""); // p{Cc} is "Other, Control" group. Taken from Constants.EDAM_NOTE_TITLE_REGEX
        // trim title if it is too long
        if (title.length() > TITLE_LEN_MAX)
            title = title.substring(0, TITLE_LEN_MAX) + "...";

        String body = visitor.toString();
        String charset = visitor.getCharset();

        if (encoding.equals(DEFAULT_CHARSET) && charset != null && !charset.equals(encoding))
        {
            try
            {
                title = convert(title, encoding, charset);
                body = convert(body, encoding, charset);
            }
            catch (UnsupportedEncodingException e)
            {
                log.log(Level.WARNING, e.getMessage(), e);
            }
        }

        return new String[]{title, body};
    }

    private static String convert(String text, String fromEnc, String toEnc) throws UnsupportedEncodingException
    {
        return new String(text.getBytes(fromEnc), toEnc);
    }

    private static class HTMLToStringVisitor
    {
        private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();

        private StringWriter stream;
        protected XMLStreamWriter streamWriter;

        private HTMLToStringVisitor() {
            try {
                stream = new StringWriter();
                streamWriter = OUTPUT_FACTORY.createXMLStreamWriter(stream);
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        }

        public void visitDocument(TagNode node)
        {
            try
            {
                streamWriter.writeStartDocument();
                visit(node);
                streamWriter.writeEndDocument();
            }
            catch (XMLStreamException e)
            {
                log.log(Level.WARNING, e.getMessage(), e);
            }
        }

        public void visit(BaseToken token)
        {
            if (token instanceof TagNode)
                visit(((TagNode)token));
            else if (token instanceof ContentNode)
                visit((ContentNode)token);
        }

        public void visit(TagNode element) {
            try
            {
                String tagName = element.getName();
                @SuppressWarnings("unchecked")
                List<BaseToken> childNodes = element.getChildren();
                boolean emptyElement = childNodes.isEmpty();

                if (!emptyElement)
                    streamWriter.writeStartElement(tagName);
                else
                    streamWriter.writeEmptyElement(tagName);

                visit(element.getAttributes());
                visit(childNodes);

                if (!emptyElement)
                    streamWriter.writeEndElement();
            }
            catch (XMLStreamException e)
            {
                log.log(Level.WARNING, e.getMessage(), e);
            }
        }

        public void visit(Map.Entry<String, String> attr)
        {
            writeAttr(attr.getKey(), attr.getValue());
        }

        protected void writeAttr(String name, String value) {
            try {
                streamWriter.writeAttribute(name, HtmlUtils.htmlUnescape(value));
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        }

        public void visit(ContentNode text) {
            try {
                streamWriter.writeCharacters(unescape(text));
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        }

        protected String unescape(ContentNode text)
        {
            return HtmlUtils.htmlUnescape(text.getContent().toString());
        }

        public void visit(List<BaseToken> nodeList)
        {
            for (BaseToken token : nodeList)
                visit(token);
        }

        public void visit(Map<String, String> attributes) {
            for (Map.Entry<String, String> attribute : attributes.entrySet())
                visit(attribute);
        }

        @Override
        public String toString() {
            return stream.toString();
        }
    }
    
    private static class HTMLToEvernoteVisitor extends HTMLToStringVisitor
    {
        private static com.wutka.dtd.DTD DTD;

        static
        {
            try
            {
                DTD = new DTDParser(new InputStreamReader(FullPageService.class.getResourceAsStream("enml2.dtd"))).parse();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        private static final Set<String> IGNORED_PARENT_TAGS = new HashSet<String>();
        static
        {
            IGNORED_PARENT_TAGS.add("select");
            IGNORED_PARENT_TAGS.add("script");
        }

        private URL pageUrl;
        private boolean bodyStarted = false;
        private boolean titleStarted = false;
        private String title = "";
        private String charset_;
        private Hashtable currentAttributes;

        private HTMLToEvernoteVisitor(URL pageUrl) {
            this.pageUrl = pageUrl;
        }

        public String getTitle() {
            return !title.isEmpty()
                   ? title
                   : pageUrl.toExternalForm();
        }

        public String getCharset()
        {
            return charset_;
        }

        @Override
        public void visitDocument(TagNode node)
        {
            try
            {
                streamWriter.writeStartDocument("UTF-8", "1.0");
                streamWriter.writeDTD(EVERNOTE_DTD);
                streamWriter.writeStartElement("en-note");

                visit(node);

                streamWriter.writeEndElement();
                streamWriter.writeEndDocument();
            }
            catch (XMLStreamException e)
            {
                log.log(Level.WARNING, e.getMessage(), e);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void visit(TagNode element) {
            String tagName = element.getName();

            if (bodyStarted)
            {
                if (DTD.elements.containsKey(tagName))
                {
                    DTDElement dtdElement = (DTDElement) DTD.elements.get(tagName);
                    Hashtable prevAttributes = currentAttributes;
                    currentAttributes = dtdElement.attributes;

                    super.visit(element);

                    currentAttributes = prevAttributes;
                }
                else if (!IGNORED_PARENT_TAGS.contains(tagName))
                {
                    visit(element.getChildren());
                }
            }
            else if (tagName.equals("body"))
            {
                bodyStarted = true;
                visit(element.getChildren());
                bodyStarted = false;
            }
            else if (tagName.equals("title"))
            {
                titleStarted = true;
                visit(element.getChildren());
                titleStarted = false;
            }
            else if (tagName.equals("meta"))
            {
                if ("Content-Type".equalsIgnoreCase(element.getAttributeByName("http-equiv")))
                {
                    String contentType = element.getAttributeByName("content");
                    charset_ = FullPageService.getCharset(contentType);
                }
            }
            else
            {
                visit(element.getChildren());
            }
        }

        @Override
        public void visit(ContentNode text) {
            if (titleStarted)
                title += unescape(text).replaceAll("\n", "").trim(); // TODO: trim() doesn't remove &nbsp; = 160
            if (bodyStarted)
                super.visit(text);
        }

        @Override
        public void visit(Map.Entry<String, String> attr) {
            String attrName = attr.getKey();
            if (currentAttributes.containsKey(attrName))
            {
                if (attrName.equals("href") || attrName.equals("src"))
                {
                    String value = attr.getValue();
                    if (!value.startsWith("http") && !value.startsWith("javascript:") && !value.startsWith("callto:") && !value.startsWith("data:"))
                    {
                        try
                        {
                            value = new URL(pageUrl, value).toString();
                        }
                        catch (MalformedURLException ignored)
                        {
                        }
                    }

                    if (value.startsWith("http")) // if absolute url
                        writeAttr(attrName, value);
                }
                else
                {
                    super.visit(attr);
                }
            }
        }
    }

    private static String getContentTypeRaw(String contentType)
    {
        if (contentType == null)
            return null;
        return contentType.replace(" ", "").split(";")[0].trim();
    }

    public static String getCharset(String contentType)
    {
        if (contentType == null)
            return null;

        String charset = null;
        for (String param : contentType.replace(" ", "").split(";"))
        {
            if (param.startsWith("charset="))
            {
                charset = param.split("=", 2)[1];
                if (charset.startsWith("\"") && charset.endsWith("\""))
                    charset = charset.substring(1, charset.length() - 1);
                break;
            }
        }
        return charset != null
               ? charset.replace("win-", "windows-")
               : null;
    }

    public static void main(String args[]) throws IOException
    {
//        String url = "http://4sq.com/dkOsb8"; // redirect with NPE
//        String url = "http://mozilla.durys.net/textplain/history.txt";// text/plain - ignored
//        String url = "http://rutracker.org/forum/viewtopic.php?t=3170771"; // &amp; in title
//        String url = "http://t.co/qiLnHptS"; // hex entities in title and body
//        String url = "http://wifi-spots.com.ua/info/admin/reports/status"; // 403 error
//        String url = "http://www.bebi-born.com/wp-admin/"; // entities in title
//        String url = "http://4pda.ru/forum/index.php?showtopic=213454&st=1640";
        String url = "http://creativecommons.org/licenses/by-nc-nd/3.0/"; // multiline title
//        String url = "http://translate.google.com/translate?client=tmpg&hl=da&u=http://www.wsdeal.com&langpair=en|da";
//        String url = "http://www.webhostingtalk.com/showpost.php?p=8043451&postcount=1"; // incorrect chars in title
//        String url = "http://www.shizos-notes.com/2011/10/blog-post.html"; //An invalid XML character (Unicode: 0xb) was found in the element content of the document
//        String url = "http://smartqmid.ru/index.php?s=1d4a5681dd3171e622c5a25ce39006dc&showtopic=2416";
//        String url = "http://g.co/maps/y5y9n";
//        String url = "http://www.charodeistvo.ru/vs.htm";
//        String url = "http://lib.custis.ru/index.php/Subversion_%D0%B8%D0%BB%D0%B8_CVS%2C_Bazaar_%D0%B8%D0%BB%D0%B8_Mercurial";

        System.out.println(new FullPageService().getPageContent(url));
    }
}
