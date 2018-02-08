package hu.stoty.mybooboo;

import com.github.shyiko.mysql.binlog.BinaryLogFileReader;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.ChecksumType;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Reads Mysql/MariaDB row based binary log file, and attemptes to create an SQLscript that reverts an update statement in it.
 * The SQL script is written to stdout, erros/debug are written to stderr
 * 
 * @author stoty
 *
 */
public class MyBooBoo {

	/**
	 * @param args[0] The binlog file to be processed
	 * @param args[1] discard all events before the one with this end_log_pos   
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		
		long lastPos;
		
		boolean debug = false;
		boolean crc = false;
		
		BinaryLogFileReader reader;
		HashMap<Long, TableMapEventData> tableMap = new HashMap<>();
		
		if(System.getProperty("debug", null)!=null){
			debug=true;
		}
		
		if(System.getProperty("nohex", null)!=null){
			FieldToString.setHexEncodeString(false);
		}
		
		if(System.getProperty("crc", null)!=null){
			crc = true;;
		}
		
		try{
			if(args.length != 2){
				throw new Exception("Needs 2 arguments");
			}
			
			lastPos = Integer.parseInt(args[1]);
			File f = new File(args[0]);
			
			//CRC handling
			EventDeserializer eventDeserializer = new EventDeserializer();
			if (crc) {
				eventDeserializer.setChecksumType(ChecksumType.CRC32);
			}
			
			reader = new BinaryLogFileReader(f, eventDeserializer);
					
			Event event;
			while((event = reader.readEvent()) != null){
				
				if(debug){
					System.err.println(event);
				}
				
				EventHeaderV4 header = (EventHeaderV4)event.getHeader();
				
				if(header.getNextPosition() < lastPos){
					continue;
				}
				if(event.getHeader().getEventType() == EventType.TABLE_MAP){
					TableMapEventData mapData = (TableMapEventData) (event.getData());
					tableMap.put(mapData.getTableId(), mapData);
					if(debug){
						System.err.println("TableMapEventData found");
					}
				} else if(event.getHeader().getEventType() == EventType.UPDATE_ROWS || event.getHeader().getEventType() == EventType.EXT_UPDATE_ROWS){
					UpdateRowsEventData updateData = (UpdateRowsEventData) (event.getData());
					if(debug){
						System.err.println("UpdateRowsEventData found");
					}
					reverseUpdate(System.out, tableMap, updateData);
				} else if(event.getHeader().getEventType() == EventType.DELETE_ROWS || event.getHeader().getEventType() == EventType.EXT_DELETE_ROWS){
					DeleteRowsEventData deleteData = (DeleteRowsEventData) (event.getData());
					if(debug){
						System.err.println("DeleteRowsEventData found");
					}
					reverseDelete(System.out, tableMap, deleteData);
				} else if(event.getHeader().getEventType() == EventType.XID){
					break;
				}
				//Maybe handle INSERTs some day ?
			
			}
		} catch (Exception e){
			printUsage(e);
			e.printStackTrace(System.err);
		}
	}
	
	private static String esacapeApostrophe(String input){
		return input.replaceAll("'", "''");
	}
	
	private static void reverseDelete(PrintStream out, HashMap<Long, TableMapEventData> tableMap, DeleteRowsEventData deleteData) throws IOException{
		
		long tableId = deleteData.getTableId();
		TableMapEventData tableData = tableMap.get(tableId);
		
		int lastColumnIndex = deleteData.getIncludedColumns().length();

		
		//create and fill in the column name variables
		String schemaName = tableData.getDatabase();
		String tableName = tableData.getTable();
		
		for(int position = 0; position < lastColumnIndex; position++){
			out.println("SELECT COLUMN_NAME INTO @"+tableName+"_"+position+" FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='"+schemaName+"' AND TABLE_NAME='"+tableName+"' and ordinal_position="+(position+1)+";");
		}
		
		//create the insert statements
		for(Serializable[] record : deleteData.getRows() ){
			
			ArrayList<String> prep = new  ArrayList<>();
			
			prep.add("'INSERT INTO "+tableData.getTable()+" SET '\n");
			boolean first=true;
			for(int pos=0; pos<deleteData.getIncludedColumns().length(); pos++){
				if(!deleteData.getIncludedColumns().get(pos)){
					continue;
				}
				if(!first){
					prep.add("' , '");
				}
				first = false;
				prep.add("@"+tableName+"_"+pos);
				prep.add("'=" + esacapeApostrophe(FieldToString.fieldToString(record[pos], tableData.getColumnTypes()[pos]))+"'\n");
			}
			
			prep.add("';'");
			
			out.print("SET @INSERT_STMT = concat("+String.join(",", prep)+");\n");
			out.print("PREPARE PREPARED_INSERT FROM @INSERT_STMT;\n");
			out.print("EXECUTE PREPARED_INSERT;\n");
			out.print("DEALLOCATE PREPARE PREPARED_INSERT;\n\n");
		}
	}
	
	private static void reverseUpdate(PrintStream out, HashMap<Long, TableMapEventData> tableMap, UpdateRowsEventData updateData) throws IOException{
		
		long tableId = updateData.getTableId();
		TableMapEventData tableData = tableMap.get(tableId);
		
		int lastColumnIndex=Math.max(updateData.getIncludedColumnsBeforeUpdate().length(), updateData.getIncludedColumns().length());

		
		//create and fill in the column name variables
		String schemaName = tableData.getDatabase();
		String tableName = tableData.getTable();
		
		for(int position = 0; position < lastColumnIndex; position++){
			out.println("SELECT COLUMN_NAME INTO @"+tableName+"_"+position+" FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='"+schemaName+"' AND TABLE_NAME='"+tableName+"' and ordinal_position="+(position+1)+";");
		}
		
		//create the update statements
		for(Entry<Serializable[], Serializable[]> record : updateData.getRows() ){
			
			Serializable[] before = record.getKey();
			Serializable[] after = record.getValue();

			ArrayList<String> prep = new  ArrayList<>();

			
			
			prep.add("'UPDATE "+tableData.getTable()+" SET '\n");
			boolean first=true;
			for(int pos=0; pos<updateData.getIncludedColumnsBeforeUpdate().length(); pos++){
				if(!updateData.getIncludedColumnsBeforeUpdate().get(pos)){
					continue;
				}
				if(!first){
					prep.add("' , '");
				}
				first = false;
				prep.add("@"+tableName+"_"+pos);
				prep.add("'=" + esacapeApostrophe(FieldToString.fieldToString(before[pos], tableData.getColumnTypes()[pos]))+"'\n");
			}
			prep.add("' WHERE '\n");
			
			first=true;
			for(int pos=0; pos<updateData.getIncludedColumns().length(); pos++){
				if(!updateData.getIncludedColumns().get(pos)){
					continue;
				}
				if(!first){
					prep.add("' AND '");
				}
				first=false;
				prep.add("@"+tableName+"_"+pos);
				prep.add("'="+esacapeApostrophe(FieldToString.fieldToString(after[pos], tableData.getColumnTypes()[pos]))+"'\n");
			}
			prep.add("';'");
			
			out.print("SET @UPDATE_STMT = concat("+String.join(",", prep)+");\n");
			out.print("PREPARE PREPARED_UPDATE FROM @UPDATE_STMT;\n");
			out.print("EXECUTE PREPARED_UPDATE;\n");
			out.print("DEALLOCATE PREPARE PREPARED_UPDATE;\n\n");
		}
	}

	private static void printUsage(Exception e) {
		System.err.println("usage: mybooboo <binlog file name> <the end_log_pos field of the first record to be processed>");
		System.err.println("example: mybooboo /home/user/mariadb-bin.000001 57323");
		System.err.println(e.getMessage());
	}

}
