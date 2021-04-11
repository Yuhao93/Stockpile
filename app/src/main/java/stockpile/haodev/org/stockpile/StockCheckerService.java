package stockpile.haodev.org.stockpile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockCheckerService extends Service {
  private static final int NOTIFICATION_ID = 10001;
  private static final int MILLIS_TO_WAIT = 10000;
  private static final Pattern PRODUCT_REGEX = Pattern.compile("p=\"(\\d+)\"");

  private CheckThread checkThread;
  private StockCheckerServiceBinder binder;

  @Override
  public IBinder onBind(Intent intent) {
    binder = new StockCheckerServiceBinder();
    return binder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    binder = null;
    return false;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);
    startForeground(5, new Notification.Builder(this)
            .setOngoing(true)
            .setColor(0xFFEE6E73)
            .setContentTitle("Checking Stock")
            .setSmallIcon(R.drawable.ic_shopping_cart_white_48dp)
            .build());
    if (checkThread == null) {
      checkThread = new CheckThread(this);
      checkThread.start();
    } else {
      checkThread.update();
    }
    return START_STICKY;
  }

  class StockCheckerServiceBinder extends Binder {
    private StockConfigurationActivity boundActivity;

    public void bindActivity(StockConfigurationActivity activity) {
      boundActivity = activity;
    }

    public void unbindActivity() {
      boundActivity = null;
    }

    void updateIfBound() {
      boundActivity.invalidateContainer();
    }
  }

  private class CheckThread extends Thread {
    private Context context;
    private TrackedItems itemsToCheck;
    private NotificationManager notificationManager;

    CheckThread(Context context) {
      this.context = context;
      itemsToCheck = new TrackedItems(context);
      notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    }

    void update() {
      itemsToCheck = new TrackedItems(context);
    }

    @Override
    public void run() {
      while (true) {
        try {
          Set<String> found = sendRequest();
          Set<String> intersection = intersections(found, itemsToCheck.activeIds());
          if (!intersection.isEmpty()) {
            TrackedItems.Item item = itemsToCheck.itemById(intersection.iterator().next());
            notify(item.id, item.name);
            itemsToCheck.setActive(item.id, false);
            if (binder != null) {
              binder.updateIfBound();
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        try {
          Thread.sleep(MILLIS_TO_WAIT);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    private void notify(String id, String name) {
      String orderUrl = String.format("http://nowinstock.net/get%s", id);
      if (id.equals("0")) {
        orderUrl = "https://stgensccweb.sccgov.org/covid19-vaccine/vaccine_49er.html";
      }
      Intent notifyIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(orderUrl));
      notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
      notifyIntent.setPackage("com.android.chrome");
      // Creates the PendingIntent
      PendingIntent pendingIntent =
          PendingIntent.getActivity(
              context,
              0,
              notifyIntent,
              PendingIntent.FLAG_UPDATE_CURRENT);

      Notification notification =
          new Notification.Builder(context)
              .setAutoCancel(true)
              .setContentIntent(pendingIntent)
              .setColor(0xFFEE6E73)
              .setContentTitle(name)
              .setContentText(String.format("%s is available.", name))
              .setSmallIcon(R.drawable.ic_shopping_cart_white_48dp)
              .setVibrate(vibrationPattern())
              .build();
      notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private Set<String> intersections(Set<String> s1, Set<String> s2) {
      Set<String> sA = s1.size() > s2.size() ? s2 : s1;
      Set<String> sB = s1.size() > s2.size() ? s1 : s2;
      Set<String> sC = new HashSet<>();
      for (String s : sA) {
        if (sB.contains(s)) {
          sC.add(s);
        }
      }
      return sC;
    }

    private Set<String> sendRequest() throws Exception {
      InputStream response = new URL("http://www.nowinstock.net/xml/remoteAvail.xml").openStream();
      Log.i("STOCK CHECKER", "FETCHING STOCK");
      Scanner scanner = new java.util.Scanner(response).useDelimiter("\\A");
      String xmlResponse = scanner.hasNext() ? scanner.next() : "";
      Matcher product = PRODUCT_REGEX.matcher(xmlResponse);
      Set<String> products = new HashSet<>();
      int prevIndex = 0;
      while (product.find(prevIndex)) {
        prevIndex += product.group(0).length();
        products.add(product.group(1));
      }
      System.out.println(products);
      if (countOccurence("https://api-prod.nvidia.com/direct-sales-shop/DR/products/en_us/USD/5438481700", "PRODUCT_INVENTORY_IN_STOCK") > 0) {
        products.add("52675");
      }
      if (countOccurence("https://www.bestbuy.com/site/nvidia-geforce-rtx-3080-10gb-gddr6x-pci-express-4-0-graphics-card-titanium-and-black/6429440.p?skuId=6429440", "btn-leading-ficon") > 0) {
        products.add("52923");
      }
      if (hasSelector("https://www.bestbuy.com/site/nvidia-geforce-rtx-3070-8gb-gddr6-pci-express-4-0-graphics-card-dark-platinum-and-black/6429442.p?skuId=6429442", ".add-to-cart-button:not([disabled])")) {
        products.add("52924");
      }
      if (vaccineFieldCount("https://schedulecare.sccgov.org/MyChartPRD/OpenScheduling/OpenScheduling/GetOpeningsForProvider?noCache=" + Math.random()) > 0) {
        products.add("0");
      }
      return products;
    }

    private int vaccineFieldCount(String url) throws Exception {
      HttpClient httpclient = new DefaultHttpClient();
      HttpPost httppost = new HttpPost(url);

      // Request parameters and other properties.
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("id", "132723"));
      params.add(new BasicNameValuePair("vt", "1277"));
      params.add(new BasicNameValuePair("dept", "101064004"));
      params.add(new BasicNameValuePair("view", "grouped"));
      params.add(new BasicNameValuePair("start", "2021-04-15"));
      params.add(new BasicNameValuePair("filters", "{\"Providers\":{},\"Departments\":{},\"DaysOfWeek\":{},\"TimesOfDay\":\"both\"}"));
      httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

      httppost.setHeader("__RequestVerificationToken", "jNy6o77-T4equkbqJ9V8D-VNmFpR3sEJjlKxZvgJGpriU-bl6xlneZ5EZz12qcnyrL1VIfm6sDmrJRjMAaAzHNXVqyg1");
      httppost.setHeader("Cookie", "visid_incap_1871648=CtwQQ6hHTYGI9GvdsmR083kIPl8AAAAAQUIPAAAAAABkxMuAZbl2zr2gA/Q8bxMm; MyChart_Session=to2i4vvnxeby1ekp045dswyn; __RequestVerificationToken_L015Q2hhcnRQUkQ1=GgWOR9hNWLe_1dMjPR9CFHBgk45a8Txi9Mi1iiKnCIZFwhMrJotbtbiSv65OxqByBoYxJeYZZ89lpTV5PBFPmiR5I301; MyChartLocale=en-US; MyChartPersistence=1083972524.47873.0000; .WPCOOKIE4mychartprd=BA61CB47396A3EAB9D5A47B34B2EB6CD0D469B70426AEF58DEA19487131179EA2FA2DBA9718DD85CFC8095CD238E15F7EF3B9894193F6A025B743778DADDAF6904174E490E34B6D92EA23849F7E3D04A19CE4DD0A7417514406644BA43A2D068F460A28C78CABF3397A6C8C6CEAE5346DE19A82212D4B64903361BAC4EB8917722CB45919BF418D7A182D4D4FC66A28611DC3910F6D88ACF30506CAB34713BA45E25C38F73591CCD7CDFB77562C045066D3AEFC668F33003EF385FB075A1B72FB41621D676471F07ECE05752C391A19BCF842A3ED693AE777D9EA7EDF655B613AA9D6330B4C75F3C5172901BF528A472F46ADD3C");

      //Execute and get the response.
      HttpResponse response = httpclient.execute(httppost);
      HttpEntity entity = response.getEntity();

      if (entity != null) {
        try (InputStream instream = entity.getContent()) {
          Scanner scanner = new java.util.Scanner(instream).useDelimiter("\\A");
          String pageResponse = scanner.hasNext() ? scanner.next() : "";
          return JsonParser.parseString(pageResponse).getAsJsonObject().get("AllDays").getAsJsonObject().size();
        }
      }
      return 0;
    }

    private boolean hasSelector(String url, String selector) throws Exception {
      InputStream response = new URL(url).openStream();
      Scanner scanner = new java.util.Scanner(response).useDelimiter("\\A");
      String pageResponse = scanner.hasNext() ? scanner.next() : "";
      Document doc = Jsoup.parse(pageResponse);
      Elements btns = doc.select(selector);
      return btns.size() > 0;
    }

    private int countOccurence(String url, String key) throws IOException {
      InputStream response = new URL(url).openStream();
      Scanner scanner = new java.util.Scanner(response).useDelimiter("\\A");
      String pageResponse = scanner.hasNext() ? scanner.next() : "";
      Pattern pattern = Pattern.compile(key);
      Matcher matcher = pattern.matcher(pageResponse);
      int cnt = 0;
      while (matcher.find()) {
        cnt++;
      }
      return cnt;
    }

    private long[] vibrationPattern() {
      long[] pattern = new long[100];
      for (int i = 1; i < pattern.length; i++) {
        pattern[i] = 1000;
      }
      return pattern;
    }
  }
}
