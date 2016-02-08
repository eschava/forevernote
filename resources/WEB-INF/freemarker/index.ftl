<html>
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
<head><title>Forevernote</title></head>
<body>
<a href="#" onclick="openPopup('evernote-login', 'Evernote login')">Login to evernote</a> <br/>

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

