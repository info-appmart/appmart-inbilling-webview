# Appmartアプリ内課金: webView (javascript)

![last-version](http://img.shields.io/badge/last%20version-1.1-green.svg "last version:1.1") 

![license apache 2.0](http://img.shields.io/badge/license-apache%202.0-brightgreen.svg "licence apache 2.0")

AppmartのWebView用のアプリ内課金システムのプラグインです。このサンプルをfork・cloneしていただき、自由にご利用ください。 

このサンプルの対象サービスは:

+ アプリ内課金：都度決済 

---

## 目次

```
1- 導入手順

	I- pluginを導入（project型）
		- パーミッション設定
		- サンプルをclone
		- Workspaceに追加 (Eclipse)
		- androidプロジェクトに導入（Eclipse）

	II- プラグイン組み込み
		- Activityクラスを変更
		- Javaとの連動
		- HTMLを変更

2- リファレンス

	I- エラーメッセージ

```



---


## 導入手順


### pluginを導入（project型）


#### パーミッション設定

> [AndroidManifest.xml]にパーミッションを追加します。

```xml
<!-- 課金API用 -->
<uses-permission android:name="jp.app_mart.permissions.APPMART_BILLING" />
```

#### サンプルをclone

```shell
cd /home/user/your_directory
git clone https://github.com/info-appmart/appmart-inbilling-webview.git
```

> 注意点：　Eclipseにうまく読み込まれないために、workspace以外のフォルダーにcloneしてください。


#### Workspaceに追加 (eclipse)

+ ⇒ File
+ ⇒ Import
+ ⇒ Existing Android Code Into Workspace
+ ⇒ 先ほどcloneしたプロジェクトを選択

![Eclipse:appmart webview](http://s27.postimg.org/8npml8ksz/appmart_inbilling_webview.png "Eclipse:appmart webview")

#### androidプロジェクトに導入（eclipse）

+ ⇒ androidプロジェクトに右クリック　
+ ⇒  Properties（プロパティー） 
+ ⇒ Android
+ ⇒ Libraries : Add （追加）(Pluginを選択)

![Eclipse:appmart webview](http://s15.postimg.org/97ltrkae3/webview_plugin.png "Eclipse:appmart webview")

### プラグイン組み込み

#### Activityクラスを変更

> Oncreate methodを更新


```java
//javascriptInterfaceオブジェクト
private MyJavascriptInterface mc;
	
//開発者情報
public String devId = "YOUR_DEVELOPPER_ID";	//デベロッパーID
public String licenceKey = "YOUR_LICENCE_KEY";	//セキュリティコード
public String publicKey= "YOUR_PUBLIC_KEY";	//公開鍵
public String appId= "YOUR_APP_ID";		//アプリID

@Override
public void onCreate(Bundle savedInstanceState){
	
    super.onCreate(savedInstanceState);

    ...
    WebView webview = (webView) findViewById(R.id.your_web_view);

    //下記3行を追記
    mc = new MyJavascriptInterface(this);
    webview.addJavascriptInterface(mc, "appmart");
    webview.getSettings().setJavaScriptEnabled(true);
    
    //いつも通りのコード
}
    
```

> 【開発情報】を書き換えてください。デベロッパー管理画面よりご確認いただけます。(サービス管理>検索>アプリ名)


![Eclipse:appmart webview](http://s21.postimg.org/h5xp3grw7/appmart_info.png "Eclipse:appmart webview")


#### Javaとの連動

> Javaと連動するために、JavascriptInterfaceクラスを用意します。 

> 内部クラスのため、**activityクラス内に定義してください**。　callbackオブジェクト経由で決済画面よりのデータを受け取ります。決済画面より戻ってきた時に[callback]の[settlementWaitValidation]が呼ばれます。エンドユーザーにコンテンツを提供して、[plugin]の[confirmSettlement]メッソードで決済を確定させます。決済確定の結果は[callback]の[settlementValidated]になります。

```java
class MyJavascriptInterface{
	
	Context mContext;
	AppmartPlugin plugin;
	    	
	/* constructor */
	public MyJavascriptInterface(Context context){
		mContext= context;
	}
		
	/* 決済実行 */
	@JavascriptInterface
	public void doSettlement(String serviceId){
		
		//Callbackオブジェクト
		AppmartResultInterface callback = new AppmartResultInterface(){
			
			//決済ID
			String transactionId;

			/* エラー発生時呼び出し */
			@Override
			public void settlementError(int errorCode) {
				Toast.makeText(mContext, "エラー発生：　errorCode: "+errorCode, Toast.LENGTH_LONG).show();					
			}

			/* 決済画面から戻ってきた時に呼び出し（決済はまだ未確定！）　*/
			@Override
			public void settlementWaitValidation(String transactionId) {
				
				// 決済IDを保存
				this.transactionId = transactionId;
				
				//TODO ユーザーにコンテンツを提供
				
				//決済を確定
				plugin.confirmSettlement();
				
			}

			/* 決済が確定された際に呼び出し */
			@Override
			public void settlementValidated(boolean result) {
				if(result)
				Toast.makeText(mContext, "決済が確定されました。決済ID: "+transactionId, Toast.LENGTH_LONG).show();
				else
				Toast.makeText(mContext, "決済が確定されませんでした", Toast.LENGTH_LONG).show();					
			}
			
		};
		    		
		
		plugin = AppmartPlugin.getInstance(mContext); 		
		plugin.setParameters(devId, licenceKey, publicKey, appId);
		plugin.doSettlement(serviceId, callback);
	}
	
}
```

#### HTML変更

> ボタンをクリックする際にjavascriptでMyJavascriptInterfaceのdoSettlement関数を呼び出します。[testId]を変更してください


```html
<script>       

    var alreadyClicked = new Array(); 

    function do_settlement(obj, itemId){

    	for (var i = 0; i < alreadyClicked.length; i ++) {
        		if(alreadyClicked[i] == itemId.id){
        			alert("既にクリックされました");
        			return;
        		}        		
        	}
        	
    	//重複クリック防止
		alreadyClicked.push(itemId.id);

    	//決済	
    	window.appmart.doSettlement(itemId);
    }
</script>

<button id="first_button" onclick="do_settlement(this, 'your_service_id')" value="settlement">Settlement</button>

```

##  リファレンス

### エラーメッセージ


| エラーコード     |説明  |
|---------- | ---|
|-1|サービスIDがnull|
|-2|パラメータ問題|
|-3|例外発生|
|-22|service　bind不可能。|
