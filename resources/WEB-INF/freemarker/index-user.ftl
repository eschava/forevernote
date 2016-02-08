<#ftl encoding="UTF-8">
<#assign user = Session.user>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
<script>
    function openPopup(url, title) {
        var popW, popH, topPos, leftPos;
        popW = 1000;
        popH = 400;
        topPos = (screen.height - popH) / 2;
        leftPos = (screen.width - popW) / 2;
        window.open(url, title, "location=0,status=0,scrollbars=1,menubar=0,toolbar=0,width=" + popW + ",height=" + popH + ",top=" + topPos + ",left=" + leftPos);
    };
</script>
<head><title>Forevernote</title>
</head>
<body>
Hello, ${user.username}! <br/>
<br/>

<b>Settings</b><br/>
<#assign selectedAttr>selected="selected"</#assign>
<#assign checkedAttr>checked="checked"</#assign>
<form action="updateSettings" method="POST">
    <table>
        <tr><td>E-mail:</td><td><input type="text" name="email" style="width:300px" value="${user.email!""}"/></td></tr>
        <tr>
            <td>Selected notebooks:</td>
            <td>
                <select name="notebooks" multiple="multiple" style="width:300px">
                    <#list Session.notebooks?keys as guid>
                        <#assign selected>${user.notebooks?seq_contains(guid) ? string(selectedAttr, "")}</#assign>
                        <option value="${guid}" ${selected}>${Session.notebooks[guid]}</guid>
                    </#list>
                </select>
            </td>
        </tr>
        <tr>
            <td>Skip selected notebooks</td>
            <td>
                <input type="checkbox" name="ignoreNotebooks" ${user.notebooksToIgnore?string(checkedAttr, "")} value="true"/>
            </td>
        </tr>
        <tr><td colspan="2" align="center"><input type="submit" value="Update"/></td></tr>
    </table>
</form>

<br/>
<br/>

<!-- Twitter news -->
<script charset="utf-8" src="http://widgets.twimg.com/j/2/widget.js"></script>
<script>
new TWTR.Widget({
  version: 2,
  type: 'profile',
  rpp: 4,
  interval: 30000,
  width: 250,
  height: 300,
  theme: {
    shell: {
      background: '#333333',
      color: '#ffffff'
    },
    tweets: {
      background: '#000000',
      color: '#ffffff',
      links: '#4aed05'
    }
  },
  features: {
    scrollbar: false,
    loop: false,
    live: false,
    behavior: 'all'
  }
}).render().setUser('forevernote_ru').start();
</script>

</body>
</html>

