<h3>About</h3>
Forevernote is a service connected to Evernote account and archiving all web pages and files that are referenced from notes.<br/>
Is implemented using Java and <a href="https://dev.evernote.com/">Evernote API</a>.<br/>
Service should be run at <a href="https://appengine.google.com/">Google AppEngine</a> hosting.<br/>
More details you can get in my post about this service in <a href="http://habrahabr.ru/post/140829/">Russian</a> or translated to <a href="https://goo.gl/ZvZq9S">English</a>.<br/>
News about project are <a href="https://twitter.com/forevernote_ru">here</a> (in Russian).<br/>

<h3>Sample installation</h3>
Fully working Forevernote installation is run at the <a href="http://evernote-offline.appspot.com/">Evernote-Offline</a>

<h3>Setup</h3>
To setup own installation of Forevernote need to:<br/>
<ul>
  <li>Install <a href="https://maven.apache.org/">Maven3</a></li>
  <li><a href="https://github.com/eschava/forevernote/archive/master.zip">Download</a> or <a href="https://github.com/eschava/forevernote.git">checkout</a> Forevernote sources from this site</li>
  <li>Get Evernote developer API keys from <a href="https://dev.evernote.com/">Evernote Developers</a></li>
  <li>Create new <a href="https://appengine.google.com/">Google AppEngine</a> application</li>
  <li>Adjust <i>src/com/eschava/forevernote/Constants.java</i> file with Evernote API keys and other sensitive data</li>
  <li>Adjust <i>resources/WEB-INF/appengine-web.xml</i> file with name of AppEngine application</li>
  <li>Execute <i>mvn gae:deploy</i> and provide correct Google credentials for deploying application</li>
  <li>Open <a href="https://dev.evernote.com/support/">Evernote Dev support</a> page and request a web hook. In <i>Webhook Notification URL</i> need to specify <i>http://APPENGINE_APP_NAME.appspot.com/noteUpdated</i></li>
  <li>Wait for confirmation from Evernote Dev support team</li>
</ul>


