package jp.app_mart;

import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

import jp.app_mart.activities.R;
import jp.app_mart.service.AppmartInBillingInterface;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class AppmartPlugin {
		
		//developer info
		public String devId;
		public String licenceKey;
		public String publicKey;
		public String appId;
		public String serviceId;
				
		// aidlファイルから生成されたサービスクラス
		private AppmartInBillingInterface service;
		// 接続状態
		private boolean isConnected = false;
		// appmart package
		public static final String APP_PACKAGE = "jp.app_mart";
		// サービスパス
		public static final String APP_PATH = "jp.app_mart.service.AppmartInBillingService";
		
		//DEBUG
		private boolean isDebug = true;	
		// アプリコンテキスト
		private Context mContext;
		// thread用のhandler
		private Handler handler = new Handler();
		// pendingIntent
		PendingIntent pIntent;
		// 決済ID
		private String transactionId;
		//決済キー
		private String resultKey;
		//次回決済ＩＤ
		private String nextTransactionId;
		// BroadcastReceiver(決済後）
		private AppmartReceiver receiver;	
		//サービスをバインドするためのserviceConnectionインスタンス
		private ServiceConnection mConnection;
		
		AppmartResultInterface callback;
		
		public static final String RESULT_CODE = "resultCode";
		public static final String RESULT_KEY = "resultKey";
		public static final String PENDING = "appmart_pending_intent";
		public static final String BROADCAST = "appmart_broadcast_return_service_payment";
		public static final String SERVICE_ID = "appmart_service_trns_id";
		public static final String APPMART_RESULT_KEY = "appmart_result_key";	
		public static final String SERVICE_NEXT_ID = "appmart_service_next_trns_id";
		
		public static final int SERVICE_ID_ISSUE = -1;
		public static final int PARAMETERS_ISSUE = -2;
		public static final int EXCEPTION_ISSUE = -3;		
		public static final int SERVICE_BIND_ISSUE = -22;
				
		/* private Constructor */
		private AppmartPlugin(Context context) {
			mContext = context ;
		}
		
		/* インスタンス取得 */
		public static AppmartPlugin getInstance(Context context){ 		
			return new AppmartPlugin(context);					
		}
		
		/* パラメータ設定 */
		public AppmartPlugin setParameters(String devId,String  licenceKey, String  publicKey, String appId){
			this.devId=devId;
			this.licenceKey = licenceKey;
			this.publicKey = publicKey;
			this.appId = appId;			
			return this;
		}
		
		
		/* 初期設定  */
		public void doSettlement(final String serviceId, final AppmartResultInterface callback){

			this.callback = callback;
			
			if(serviceId == null) callback.settlementError(SERVICE_ID_ISSUE);
			
			this.serviceId = serviceId;

			// 決済後のbroadcastをキャッチ
			setReceiver();

			// appmartサービスに接続するためのIntentオブジェクトを生成
			Intent i = new Intent();
			i.setClassName(APP_PACKAGE, APP_PATH);
			if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
				debugMess(mContext.getString(R.string.no_appmart_installed));
				return;
			}

			// Service Connectionインスタンス化
			mConnection = new ServiceConnection() {
				
				//接続時実行
				public void onServiceConnected(ComponentName name,
						IBinder boundService) {
					//Ｓｅｒｖｉｃｅクラスをインスタンス化
					service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
					isConnected = true;
					startSettlement();
				}
				//切断時実行
				public void onServiceDisconnected(ComponentName name) {
					service = null;
				}
			};

			// Handler初期化
			handler = new Handler() {
				@SuppressLint("HandlerLeak")
				@Override
				public void handleMessage(Message msg) {
					switch (msg.what) {
					case 1: // pendingIntent取得
						accessPaymentPage();
						break;
					case 2:// パラメータNG
						debugMess(mContext.getString(R.string.wrong_parameters));
						callback.settlementError(PARAMETERS_ISSUE);
						break;
					case 3:// 例外発生
						debugMess(mContext.getString(R.string.exception_occured));
						callback.settlementError(EXCEPTION_ISSUE);
						break;
					case 10:// 決済最終確認完了			
						callback.settlementValidated(true);
						unbindService();
						break;
					case -10:// 決済最終確認エラー
						debugMess(mContext.getString(R.string.settlement_not_confirmed));
						callback.settlementValidated(false);
						break;
					}
				}
			};
			
			
			// bindServiceを利用し、サービスに接続
			try {
				mContext.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
			} catch (Exception e) {
				e.printStackTrace();
				callback.settlementError(SERVICE_BIND_ISSUE);
				return;
			}
			
		}
		
		/* 決済開始 */
		private void startSettlement(){
			
			new Thread(new Runnable() {
				public void run() {
					try {

						// 必要なデータを暗号化
						String dataEncrypted = createEncryptedData(	serviceId, devId, licenceKey, publicKey);

						// サービスのprepareForBillingServiceメソッドを呼びます
						Bundle bundleForPaymentInterface = service.prepareForBillingService(appId, dataEncrypted);

						if (bundleForPaymentInterface != null) {
							
							int statusId = bundleForPaymentInterface.getInt(RESULT_CODE);
							
							if (statusId != 1) {
								handler.sendEmptyMessage(2);
								return;
							} else {

								// PendingIntentを取得
								pIntent = bundleForPaymentInterface.getParcelable(PENDING);
								
								// 決済キーを取得
								resultKey= bundleForPaymentInterface.getString(RESULT_KEY);
								
								// mainUIに設定
								handler.sendEmptyMessage(1);
							}

						}

					} catch (Exception e) {
						handler.sendEmptyMessage(3);
						e.printStackTrace();
					}

				}
			}).start();
		}
		
		/* Service unbind */
		private void unbindService(){
			mContext.unbindService(mConnection);
			service = null;
			mContext.unregisterReceiver(receiver); 
		}
		
		/*　BroadcastReceiverの設定 */
		private void setReceiver() {
			// Broadcast設定
			IntentFilter filter = new IntentFilter(BROADCAST);
			receiver = new AppmartReceiver();
			mContext.registerReceiver(receiver, filter);
		}


		/* 課金画面へリダイレクト */
		private void accessPaymentPage() {
			try {
				pIntent.send(mContext, 0, new Intent());
			} catch (CanceledException e) {
				e.printStackTrace();
			}
		}

		/* debug用 */
		private void debugMess(String mess) {
			if (isDebug) {
				Log.d("DEBUG", mess);
				Toast.makeText(mContext, mess, Toast.LENGTH_SHORT).show();
			}
		}

		
		/*決済完了後のbroadcastをcatchするReceiverクラス */
		private class AppmartReceiver extends BroadcastReceiver {

			@Override
			public void onReceive(Context arg0, Intent arg1) {

				try {

					// 決済ＩＤを取得
					transactionId = arg1.getExtras().getString(SERVICE_ID);
					
					//決済キー
					String resultKeyCurrentStransaction= arg1.getExtras().getString(APPMART_RESULT_KEY);
					
					//Appmart1.2以下は決済キーが発行されない
					if (resultKeyCurrentStransaction==null || resultKeyCurrentStransaction.equals(resultKey)){
									
						// 継続決済の場合は次回決済ＩＤを取得
						nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);
		
						// コンテンツを提供し、ＤＢを更新
						callback.settlementWaitValidation(transactionId);
					
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		
		public void confirmSettlement(){
			
			// 決済を確認
			(new Thread(new Runnable() {
				public void run() {

					try {

						int res = service.confirmFinishedTransaction(transactionId, serviceId,devId);

						if (res == 1)
							handler.sendEmptyMessage(10);
						else
							handler.sendEmptyMessage(-10);
						
					} catch (Exception e) {
						handler.sendEmptyMessage(3);
						e.printStackTrace();
					}

				}
			})).start();
		}
		

		/* 引数暗号化 */
		public String createEncryptedData(String serviceId, String developId,
				String strLicenseKey, String strPublicKey) {

			final String SEP_SYMBOL = "&";
			StringBuilder infoDataSB = new StringBuilder();
			infoDataSB.append(serviceId).append(SEP_SYMBOL);

			// デベロッパID引数を追加
			infoDataSB.append(developId).append(SEP_SYMBOL);

			// ライセンスキー引数を追加
			infoDataSB.append(strLicenseKey);

			String strEncryInfoData = "";

			try {
				KeyFactory keyFac = KeyFactory.getInstance("RSA");
				KeySpec keySpec = new X509EncodedKeySpec(Base64.decode(
						strPublicKey.getBytes(), Base64.DEFAULT));
				Key publicKey = keyFac.generatePublic(keySpec);

				if (publicKey != null) {
					Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
					cipher.init(Cipher.ENCRYPT_MODE, publicKey);

					byte[] EncryInfoData = cipher.doFinal(infoDataSB.toString()
							.getBytes());
					strEncryInfoData = new String(Base64.encode(EncryInfoData,
							Base64.DEFAULT));
				}

			} catch (Exception ex) {
				ex.printStackTrace();
				strEncryInfoData = "";
				debugMess(mContext.getString(R.string.data_encryption_failed));
			}

			return strEncryInfoData.replaceAll("(\\r|\\n)", "");

		}
	
	
}
