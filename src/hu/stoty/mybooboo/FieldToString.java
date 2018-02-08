package hu.stoty.mybooboo;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.BitSet;

import com.github.shyiko.mysql.binlog.event.deserialization.ColumnType;

/**
 * Class to convert Data in Mysql native/binlog format to a text format that can be used in an sql statement
 * 
 * @author stoty
 *
 */
public class FieldToString {
	
	//To avaoid encoding problems, leave this enabled
	private static boolean hexEncodeString = true;

	private static final DateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
	private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
	private static final DateFormat timeFormatMs = new SimpleDateFormat("HH:mm:ss.S");
	private static final DateFormat dateTimeFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
	private static final DateFormat dateTimeFormatMs = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.S");

	/**
	 * @param data Column data in Mysql native format
	 * @param typeByte Column type 
	 * @return The data in a textual format suitable for insert/update statements
	 * @throws IOException
	 */
	public static String fieldToString(Serializable data, byte typeByte) throws IOException {
		
		ColumnType type = ColumnType.byCode((int) typeByte & 0xFF);
		
		if (data == null) {
			return "NULL";
		}
		
		switch (type) {
		case BIT:
			return bitToString(data);
		case TINY:
		case SHORT:
		case INT24:
		case LONG:
			return intToString(data);
		case LONGLONG:
			return longToString(data);
		case FLOAT:
			return floatToString(data);
		case DOUBLE:
			return doubleToString(data);
		case NEWDECIMAL:
			return bigDecimalToString(data);
		case DATE:
			return dateToString(data);
		case TIME:
			return timeToString(data);
		case TIME_V2:
			return timeToString(data);
		case TIMESTAMP:
			return dateTimeToString(data);
		case TIMESTAMP_V2:
			return timestampMsToString(data);
		case DATETIME:
			return dateTimeToString(data);
		case DATETIME_V2:
			return dateTimeToString(data);
		case YEAR:
			return intToString(data);
		case STRING:
		case VARCHAR:
			return stringToString(data);
		case VAR_STRING:
		case BLOB:
			return blobToString(data);
		case ENUM:
			return intToString(data);
		case SET:
			return longToString(data);
		case GEOMETRY:
			return blobToString(data);
		default:
			throw new IOException("Unsupported type " + type);
		}
	}

	protected static String bitToString(Serializable in) {
		StringBuilder b = new StringBuilder();
		BitSet bitset = (BitSet) in;
		b.append("b'");
		for (int c = 0; c < bitset.length(); c++) {
			b.append(bitset.get(c) ? "1" : "0");
		}
		b.append("'");
		return b.toString();
	}

	protected static String intToString(Serializable in) {
		Integer number = (Integer) in;
		return number.toString();
	}

	protected static String longToString(Serializable in) {
		Long number = (Long) in;
		return number.toString();
	}

	protected static String floatToString(Serializable in) {
		Float number = (Float) in;
		return number.toString();
	}

	protected static String doubleToString(Serializable in) {
		Double number = (Double) in;
		return number.toString();
	}

	protected static String bigDecimalToString(Serializable in) {
		BigDecimal number = (BigDecimal) in;
		return number.toPlainString();
	}

	protected static String timeToString(Serializable in) {
		return "'"+timeFormatMs.format((java.sql.Time) in)+"'";
	}

	protected static String dateToString(Serializable in) {
		return "'"+dateFormat.format((java.sql.Date) in)+"'";
	}

	protected static String dateTimeToString(Serializable in) {
		return "'"+dateTimeFormat.format((java.util.Date) in)+"'";
	}

	protected static String timestampMsToString(Serializable in) {
		return "'"+dateTimeFormatMs.format((java.sql.Timestamp) in)+"'";
	}

	protected static String blobToString(Serializable in) {
		byte[] text = (byte[]) in;
		StringBuilder b = new StringBuilder();
		b.append("X'");
		b.append(bytesToHex(text));
		b.append("'");
		return b.toString();
	}
	
	protected static String stringToString(Serializable in) {
		if(isHexEncodeString()){
			return blobToString(in);
		} else {
			byte[] text = (byte[]) in;
			return new String(text);
		}
	}

	protected static String bytesToHex(byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 * @return true if strings are output in Hex encoded format
	 */
	public static boolean isHexEncodeString() {
		return hexEncodeString;
	}

	/**
	 * @param hexEncodeString Wheter strings should be hex encoded
	 */
	public static void setHexEncodeString(boolean hexEncodeString) {
		FieldToString.hexEncodeString = hexEncodeString;
	}
	
}
