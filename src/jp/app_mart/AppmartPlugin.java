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
				
		// aidl�t�@�C�����琶�����ꂽ�T�[�r�X�N���X
		private AppmartInBillingInterface service;
		// �ڑ����
		private boolean isConnected = false;
		// appmart package
		public static final String APP_PACKAGE = "jp.app_mart";
		// �T�[�r�X�p�X
		public static final String APP_PATH = "jp.app_mart.service.AppmartInBillingService";
		
		//DEBUG
		private boolean isDebug = true;	
		// �A�v���R���e�L�X�g
		private Context mContext;
		// thread�p��handler
		private Handler handler = new Handler();
		// pendingIntent
		PendingIntent pIntent;
		// ����ID
		private String transactionId;
		//���σL�[
		private String resultKey;
		//���񌈍ςh�c
		private String nextTransactionId;
		// BroadcastReceiver(���ό�j
		private AppmartReceiver receiver;	
		//�T�[�r�X���o�C���h���邽�߂�serviceConnection�C���X�^���X
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
		
		/* �C���X�^���X�擾 */
		public static AppmartPlugin getInstance(Context context){ 		
			return new AppmartPlugin(context);					
		}
		
		/* �p�����[�^�ݒ� */
		public AppmartPlugin setParameters(String devId,String  licenceKey, String  publicKey, String appId){
			this.devId=devId;
			this.licenceKey = licenceKey;
			this.publicKey = publicKey;
			this.appId = appId;			
			return this;
		}
		
		
		/* �����ݒ�  */
		public void doSettlement(final String serviceId, final AppmartResultInterface callback){

			this.callback = callback;
			
			if(serviceId == null) callback.settlementError(SERVICE_ID_ISSUE);
			
			this.serviceId = serviceId;

			// ���ό��broadcast���L���b�`
			setReceiver();

			// appmart�T�[�r�X�ɐڑ����邽�߂�Intent�I�u�W�F�N�g�𐶐�
			Intent i = new Intent();
			i.setClassName(APP_PACKAGE, APP_PATH);
			if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
				debugMess(mContext.getString(R.string.no_appmart_installed));
				return;
			}

			// Service Connection�C���X�^���X��
			mConnection = new ServiceConnection() {
				
				//�ڑ������s
				public void onServiceConnected(ComponentName name,
						IBinder boundService) {
					//�r�������������N���X���C���X�^���X��
					service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
					isConnected = true;
					startSettlement();
				}
				//�ؒf�����s
				public void onServiceDisconnected(ComponentName name) {
					service = null;
				}
			};

			// Handler������
			handler = new Handler() {
				@SuppressLint("HandlerLeak")
				@Override
				public void handleMessage(Message msg) {
					switch (msg.what) {
					case 1: // pendingIntent�擾
						accessPaymentPage();
						break;
					case 2:// �p�����[�^NG
						debugMess(mContext.getString(R.string.wrong_parameters));
						callback.settlementError(PARAMETERS_ISSUE);
						break;
					case 3:// ��O����
						debugMess(mContext.getString(R.string.exception_occured));
						callback.settlementError(EXCEPTION_ISSUE);
						break;
					case 10:// ���ύŏI�m�F����			
						callback.settlementValidated(true);
						unbindService();
						break;
					case -10:// ���ύŏI�m�F�G���[
						debugMess(mContext.getString(R.string.settlement_not_confirmed));
						callback.settlementValidated(false);
						break;
					}
				}
			};
			
			
			// bindService�𗘗p���A�T�[�r�X�ɐڑ�
			try {
				mContext.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
			} catch (Exception e) {
				e.printStackTrace();
				callback.settlementError(SERVICE_BIND_ISSUE);
				return;
			}
			
		}
		
		/* ���ϊJ�n */
		private void startSettlement(){
			
			new Thread(new Runnable() {
				public void run() {
					try {

						// �K�v�ȃf�[�^���Í���
						String dataEncrypted = createEncryptedData(	serviceId, devId, licenceKey, publicKey);

						// �T�[�r�X��prepareForBillingService���\�b�h���Ăт܂�
						Bundle bundleForPaymentInterface = service.prepareForBillingService(appId, dataEncrypted);

						if (bundleForPaymentInterface != null) {
							
							int statusId = bundleForPaymentInterface.getInt(RESULT_CODE);
							
							if (statusId != 1) {
								handler.sendEmptyMessage(2);
								return;
							} else {

								// PendingIntent���擾
								pIntent = bundleForPaymentInterface.getParcelable(PENDING);
								
								// ���σL�[���擾
								resultKey= bundleForPaymentInterface.getString(RESULT_KEY);
								
								// mainUI�ɐݒ�
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
		
		/*�@BroadcastReceiver�̐ݒ� */
		private void setReceiver() {
			// Broadcast�ݒ�
			IntentFilter filter = new IntentFilter(BROADCAST);
			receiver = new AppmartReceiver();
			mContext.registerReceiver(receiver, filter);
		}


		/* �ۋ���ʂփ��_�C���N�g */
		private void accessPaymentPage() {
			try {
				pIntent.send(mContext, 0, new Intent());
			} catch (CanceledException e) {
				e.printStackTrace();
			}
		}

		/* debug�p */
		private void debugMess(String mess) {
			if (isDebug) {
				Log.d("DEBUG", mess);
				Toast.makeText(mContext, mess, Toast.LENGTH_SHORT).show();
			}
		}

		
		/*���ϊ������broadcast��catch����Receiver�N���X */
		private class AppmartReceiver extends BroadcastReceiver {

			@Override
			public void onReceive(Context arg0, Intent arg1) {

				try {

					// ���ςh�c���擾
					transactionId = arg1.getExtras().getString(SERVICE_ID);
					
					//���σL�[
					String resultKeyCurrentStransaction= arg1.getExtras().getString(APPMART_RESULT_KEY);
					
					//Appmart1.2�ȉ��͌��σL�[�����s����Ȃ�
					if (resultKeyCurrentStransaction==null || resultKeyCurrentStransaction.equals(resultKey)){
									
						// �p�����ς̏ꍇ�͎��񌈍ςh�c���擾
						nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);
		
						// �R���e���c��񋟂��A�c�a���X�V
						callback.settlementWaitValidation(transactionId);
					
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		
		public void confirmSettlement(){
			
			// ���ς��m�F
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
		

		/* �����Í��� */
		public String createEncryptedData(String serviceId, String developId,
				String strLicenseKey, String strPublicKey) {

			final String SEP_SYMBOL = "&";
			StringBuilder infoDataSB = new StringBuilder();
			infoDataSB.append(serviceId).append(SEP_SYMBOL);

			// �f�x���b�pID������ǉ�
			infoDataSB.append(developId).append(SEP_SYMBOL);

			// ���C�Z���X�L�[������ǉ�
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
