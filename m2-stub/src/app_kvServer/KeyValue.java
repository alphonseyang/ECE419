package app_kvServer;


public class KeyValue {
	private String key;
	private String value;

    public KeyValue(String key, String value) {
        super();
        this.key = key;
        this.value = value;
    }

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public String convertString () {
		String value_;
		if (value == null)
			value_ = "Not Found";
		else
			value_ = value;

		return "[" + key + ": " + value_ + "]";
	}

}