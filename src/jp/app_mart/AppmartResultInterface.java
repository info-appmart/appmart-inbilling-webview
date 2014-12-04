package jp.app_mart;

public interface AppmartResultInterface {

	// Error occured
	public void settlementError(int errorCode);
	
	// Settlement is waiting for validation
	public void settlementWaitValidation(String transactionId);
	
	// Settlement validated
	public void settlementValidated(boolean result);
	
}
