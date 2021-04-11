# Stockpile

## Instructions for crawling your own website
1. Download [Android Studio](https://developer.android.com/studio/)
2. On your website that you want to crawl, find the http request you want to send. Make sure that you can reproduce the request with whatever required params, method, and headers.
3. Change the sendRequest() method in StockCheckerService to add "0" when an http request matches some condition.
4. Change the orderUrl assignment for the "0" case in the notify() method in StockCheckerService.
5. Install the app and add a tracked item with id="0".
6. As long as the shopping cart icon is in the header, the crawling is taking place. Open the app to enable the crawling.
