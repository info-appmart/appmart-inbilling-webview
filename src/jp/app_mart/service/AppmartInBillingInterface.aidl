package jp.app_mart.service;

import android.os.Bundle;

interface AppmartInBillingInterface {

    //課金前の method
    Bundle prepareForBillingService(String appId, String encryptedData);

    //サービスの詳細情報を取得
    String getServiceDetails(String serviceId, String encryptedData);

    //サービス提供後の method
    int confirmFinishedTransaction(String trnsId, String serviceId, String developerId );

    //トランザクション情報を取得
    String getPaymentDetails(String trnsId, String serviceId, String developerId);

    //次回支払情報を取得
    String getNextPaymentDetails(String nextTrnsId, String developerId, String itemId);

    //継続課金の停止
    String stopContinuePayment(String nextTrnsId, String developerId, String itemId);
    
    //【管理】サービスの購入状態
    String hasAlreadyBought (String developerId, String appId, String itemId);

}