
package com.aevi.tothemovies;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import com.aevi.helpers.UriBuilder;
import com.aevi.helpers.services.AeviServiceConnectionCallback;
import com.aevi.payment.PaymentApplicationNotInstalledException;
import com.aevi.payment.PaymentConfiguration;
import com.aevi.payment.PaymentRequest;
import com.aevi.payment.TransactionResult;
import com.aevi.payment.TransactionStatus;
import com.aevi.printing.PrintService;
import com.aevi.printing.PrintServiceProvider;
import com.aevi.printing.model.Alignment;
import com.aevi.printing.model.PrintPayload;
import com.aevi.status.ServiceState;
import com.aevi.status.ServiceStateHelper;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Currency;

public class MainActivity extends Activity implements OnClickListener{

  private static final String TAG = MainActivity.class.getSimpleName();
  private static final String PAYMENT_APPLICATION_NOT_FOUND_ERROR = "Payment Application is not installed or your application has insufficient rights to access it. This application will now exit.";

  private final MainActivity.JavascriptBridge javascriptBridge = new JavascriptBridge(this);

  private String currentPage;
  private WebView webView;
  private Currency defaultCurrency;
  private String productId;
  private String price;
  private String description;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    try {
      assertInstalledComponents();
    } catch (PaymentApplicationNotInstalledException e) {
      showExitAlertDialog(PAYMENT_APPLICATION_NOT_FOUND_ERROR);
      return;
    }

    try {
      defaultCurrency = getDefaultCurrency();
    } catch (DefaultCurrencyNotSetException e) {
      showExitAlertDialog("Default currency is not set. This application will now exit.");
      return;
    }
    
    setContentView(R.layout.activity_main);
    
//    Button helloButton =(Button)findViewById(R.id.helloButton);
//    helloButton.setOnClickListener(this);

    webView = (WebView) findViewById(R.id.webView);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setUseWideViewPort(false);
    
    webView.addJavascriptInterface(javascriptBridge, "Bridge");
    
    webView.setWebChromeClient(new WebChromeClient());
    webView.loadDataWithBaseURL("file:///android_asset/", getFileFromApplicationResources("useme_index.html"), "text/html", "utf-8", "");
  }

  private void assertInstalledComponents() {
    // Ensure the payment application/simulator is installed. If not, present an alert dialog
    // to the user and exit.
    if (ServiceStateHelper.getPaymentApplicationStatus(this) != ServiceState.AVAILABLE) {
      throw new PaymentApplicationNotInstalledException();
    }
  }

  @Override
  public void onBackPressed() {
    Log.d(TAG, "back button pressed");

    if (!currentPage.equals("/")) {
      javascriptBridge.navigate("/");
    } else {
      finish();
    }
  }

  private Currency getDefaultCurrency() {

    // Get the payment application configuration information.

    try {
      PaymentConfiguration configuration = PaymentConfiguration.getPaymentConfiguration(this);
      if (configuration.getDefaultCurrency() == null) {
        throw new DefaultCurrencyNotSetException();
      }
      return configuration.getDefaultCurrency();
    } catch (PaymentApplicationNotInstalledException ex) {
      showExitAlertDialog(PAYMENT_APPLICATION_NOT_FOUND_ERROR);
      return null;
    } catch (Exception e) {
      showExitAlertDialog("Encountered exception while trying to obtain the payment configuration");
      return null;
    }
  }

  private String getFileFromApplicationResources(String url) {

    try {
      InputStream htmlStream = getApplicationContext().getAssets().open(url);
      Reader is = new BufferedReader(new InputStreamReader(htmlStream, "UTF8"));

      final char[] buffer = new char[1024];
      StringBuilder out = new StringBuilder();
      int read;
      do {
        read = is.read(buffer, 0, buffer.length);
        if (read > 0) {
          out.append(buffer, 0, read);
        }
      } while (read >= 0);

      return out.toString();

    } catch (UnsupportedEncodingException e) {
      Log.e(TAG, "UnsupportedEncodingException", e);
      return "";
    } catch (IOException e) {
      Log.e(TAG, "IO Exception", e);
      return "";
    }
  }

  private void showExitAlertDialog(String message) {
    Log.e(TAG, message);
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(message).setCancelable(false);
    builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        finish();
      }
    });
    AlertDialog alert = builder.create();
    alert.show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == IntentIntegrator.REQUEST_CODE) {
    	//Got a barcode scanner response
    	
    	IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    	if (result != null) {
    		String contents = result.getContents();
    		Log.d(TAG, "Result is " + contents);
    		
    		if (contents.startsWith("albert:::")) {
    			// This QR Code if for albert
    			String[] arr = contents.split(":::");
    			this.productId = arr[1];
    			this.price = arr[2];
    			this.description = arr[3];
    			
    			//Go to the product page
    			webView.loadDataWithBaseURL("file:///android_asset/", 
    					getFileFromApplicationResources("product.html"), 
    					"text/html", "utf-8", "");
    			
    			
    		} else if (contents.startsWith("assign:::")) {
    			// This QR Code if for albert
//    			String[] arr = contents.split(":::");
//    			this.productId = arr[1];
//    			this.price = arr[2];
//    			this.description = arr[3];
    			
    			//Go to the product page
    			webView.loadDataWithBaseURL("file:///android_asset/", 
    					getFileFromApplicationResources("assignSeat.html"), 
    					"text/html", "utf-8", "");
    			
    			
    		} else {
    			webView.loadUrl(contents);
    		}
    		
    	} else {
    		//Not for Albert - go to page
    		Toast toast = Toast.makeText(getApplicationContext(), "Invalid QR Code", Toast.LENGTH_LONG);
    		toast.show();
    	}
    	
    	
    } else {
    	// Must be an Albert response
    	 TransactionResult transactionResult = TransactionResult.fromIntent(data);
    	 TransactionStatus transactionStatus = transactionResult.getTransactionStatus();
    	 	switch (transactionStatus) {
    	 		case APPROVED:
    	 			
    	 			webView.loadDataWithBaseURL("file:///android_asset/", 
    	 					getFileFromApplicationResources("successPayment.html"), 
    	 					"text/html", "utf-8", "");
    	 			
//    	 			javascriptBridge.navigate("/successPayment.html");
    	 			
    	 			break;
    	 			default:
    	 				webView.loadDataWithBaseURL("file:///android_asset/", 
        	 					getFileFromApplicationResources("errorPayment.html"), 
        	 					"text/html", "utf-8", "");
    	 				
//    	 			javascriptBridge.navigate("/failure");
    	 			break;
    	 	}
    }
    
  }

  private class JavascriptBridge {

    private final String tag = JavascriptBridge.class.getSimpleName();
	private MainActivity mainActivity;
    
    public JavascriptBridge(MainActivity mainActivity) {
    	this.mainActivity = mainActivity;
	}

	public void scan() {
		Log.d(tag, "Time to scan");
		IntentIntegrator scanIntegrator = new IntentIntegrator(mainActivity);
    	scanIntegrator.initiateScan();
    	
    }
	
	public void navigateAssignSeat() {
		Log.d(tag, "Time to assignSeat");
		webView.loadDataWithBaseURL("file:///android_asset/", 
				getFileFromApplicationResources("assignSeat.html"), 
				"text/html", "utf-8", "");
    	
    }
	
	public void navigateViewOrder() {
		Log.d(tag, "Time to assignSeat");
		webView.loadDataWithBaseURL("file:///android_asset/", 
				getFileFromApplicationResources("viewOrderPayment.html"), 
				"text/html", "utf-8", "");
    	
    }
	
	public void navigateMerchandisePayment() {
		Log.d(tag, "Time to assignSeat");
		webView.loadDataWithBaseURL("file:///android_asset/", 
				getFileFromApplicationResources("merchandisePayment.html"), 
				"text/html", "utf-8", "");
    	
    }
	
	public void buyProduct() {
		
		if (mainActivity.price.startsWith("$"))
			mainActivity.price = mainActivity.price.substring(1);
		
		BigDecimal parsedAmount = new BigDecimal(mainActivity.price);
		
		Log.d(TAG, "Creating a payment request for productId:" + mainActivity.productId + ", mainActivity.amount:" + parsedAmount);
		
		PaymentRequest paymentRequest = new PaymentRequest(parsedAmount);
		startActivityForResult(paymentRequest.createIntent(), Integer.parseInt(mainActivity.productId));
    	
    }
	
	public void makePayment(String amount) {
		mainActivity.price = amount;
		mainActivity.productId = "00001";
		
		buyProduct();
		
	}
	
	public String getPrice() {
		return mainActivity.price;
	}
	
	public String getProductId() {
		return mainActivity.productId;
	}
	
	public String getDescription() {
		return mainActivity.description;
	}
    
    public void buyTicket(String movieId, String amount) {
      BigDecimal parsedAmount = new BigDecimal(amount);

      Log.d(tag, "Creating a payment request for movieId:" + movieId + ", amount:" + parsedAmount);

      PaymentRequest paymentRequest = new PaymentRequest(parsedAmount);
      startActivityForResult(paymentRequest.createIntent(), Integer.parseInt(movieId));
    }

    public void navigate(final String fragment) {
      Log.d(tag, "Navigating to " + fragment);

      currentPage = fragment;

      webView.post(new Runnable() {
        public void run() {
          webView.loadUrl("javascript:location.hash='#" + fragment + "'");
        }
      });
    }

    public void enableScroll() {
      webView.setVerticalScrollBarEnabled(true);
      webView.setHorizontalScrollBarEnabled(true);
      webView.setOnTouchListener(null);
    }

    public void disableScroll() {
      webView.setVerticalScrollBarEnabled(false);
      webView.setHorizontalScrollBarEnabled(false);
      webView.setOnTouchListener(new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
          return (event.getAction() == MotionEvent.ACTION_MOVE);
        }
      });
    }

    public void exit() {
      finish();
    }

    public void log(String message) {
      Log.i(TAG, message);
    }

    public String currencyCode() {
      return defaultCurrency.getCurrencyCode();
    }

    public String currencySymbol() {
      return TextUtils.htmlEncode(defaultCurrency.getSymbol());
    }

    public String rewriteUri(String uri){
      Log.d(TAG,"Rewriting uri:"+uri);
      return new UriBuilder(MainActivity.this).rewrite(uri);
    }

    public void printTicket(final String title) {

      Log.d(tag, "Printing ticket for event:" + title);

      webView.post(new Runnable() {
        @Override
        public void run() {
          final PrintServiceProvider printServiceProvider = new PrintServiceProvider(getBaseContext());
          printServiceProvider.connect(new AeviServiceConnectionCallback<PrintService>() {
            @Override
            public void onConnect(PrintService service) {

              PrintPayload ticket = new PrintPayload();
              ticket.append(title).align(Alignment.CENTER);
              ticket.appendEmptyLine();

              BitmapFactory.Options bitmapFactoryOptions = service.getDefaultPrinterSettings().asBitmapFactoryOptions();
              Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.qr_code, bitmapFactoryOptions);
              ticket.append(logo).contrastLevel(100).align(Alignment.CENTER);

              ticket.append("Please show your ticket").align(Alignment.CENTER);
              ticket.append("at the entrance of the venue").align(Alignment.CENTER);

              ticket.appendEmptyLine();
              ticket.appendEmptyLine();
              ticket.appendEmptyLine();

              service.print(ticket);
            }
          });
        }
      });
    }
  }

@Override
public void onClick(View v) {
	
	webView.loadDataWithBaseURL("file:///android_asset/", 
			getFileFromApplicationResources("useme_index.html"), 
			"text/html", "utf-8", "");
//	webView.loadUrl("/useme_index.html");
	
}
}