# MyBooBoo

This program is meant to help recover from inadvertent destructive updates or deletes on a mysql based database.

This program MAY help you, if you have row based binary logging enabled on your database, 
and have the log file that contains inadvertent update.

This is more limited, but potentially much faster alternative to the point-in-time recovery procedure described at
http://dev.mysql.com/doc/refman/5.7/en/point-in-time-recovery.html

This is experimental code, extremely lightly tested, and is likely to have serious bugs.

### BUILDING:
The heavy lifting is done by the `mysql-binlog-connector-java` library that parses the binlog file.
You need to download and build that first.

* https://github.com/shyiko/mysql-binlog-connector-java (upstream version, MySql only)

If you use MariaDB, you may need to one of its forks with MariaDB support.

You need to copy the resulting jar to the `lib/` directory.

Run `build.sh`. This will generate the class files under the bin directory.

If you use Mysql 5.6+, and get mysterious errors while parsing the binlog file, add the -Dcrc=true paramater to mybooboo.sh

### USING:
This program takes a binlog file and a binlog position as input, and outputs an sql script 
that MAY revert the effects the update on the specified position, or MAY destroy your database completely.

First you need to find the binlog file that contains the update, and the binlog position that starts the bad update.

The binlog files are usually rotated when they reach a defined size. You should be able to find the right binlog file 
by looking at the file modification dates, and choose the one that is the earliest after the bad update statement was executed.

Once you have the file, you need to find the position. The binlog file can be dumped with following command: 
`mysqlbinlog -v /home/user/mariadb-bin.000001`

and the output will look similar to this:

```
#151227 11:19:58 server id 1  end_log_pos 554 	GTID 0-1-2
/*!100001 SET @@session.gtid_seq_no=2*//*!*/;
BEGIN
/*!*/;
# at 554
# at 607
#151227 11:19:58 server id 1  end_log_pos 607 	Table_map: `booboo`.`test` mapped to number 70
#151227 11:19:58 server id 1  end_log_pos 732 	Update_rows: table id 70 flags: STMT_END_F

BINLOG '
Trt/VhMBAAAANQAAAF8CAAAAAEYAAAAAAAEABmJvb2JvbwAEdGVzdAAEAw/2/AX/AAoCAg4=
Trt/VhgBAAAAfQAAANwCAAAAAEYAAAAAAAEABP//8AEAAAAV4XJ27Xp0P3I/IHT8a/ZyZvpyZ+lw
gAAAezkVAOFydu16dD9yPyB0/Gv2cmb6cmfpcPABAAAAFeFydu16dD9yPyB0/Gv2cmb6cmfpcIAA
AHs5BgBib29ib28=
'/*!*/;
### UPDATE `booboo`.`test`
### WHERE
###   @1=1
###   @2='�rv�zt?r? t�k�rf�rg�p'
###   @3=000000123.570000000
###   @4='�rv�zt?r? t�k�rf�rg�p'
### SET
###   @1=1
###   @2='�rv�zt?r? t�k�rf�rg�p'
###   @3=000000123.570000000
###   @4='booboo'
# at 732
#151227 11:19:58 server id 1  end_log_pos 759 	Xid = 14
COMMIT/*!*/;
...
```
You can search for the `### UPDATE database.table` string to find the update statements on the affected table. 
You are likely to have may of these, and you will need to find the bad one by the timestamp  
or the number of update commands contained in the record, or by the parameters in the SET part. 

Once you have found the first `Update_rows` record to revert, you will need to find the 
`Table_map` record that maps the table for the update record and note the end_log_pos parameter of it.

In the example the `Update_rows` record has `end_log_pos 732`, and the `Table_map` record has `end_log_pos 607`, 
so you will need to specify `607` as a parameter to MyBooBoo.

In this case the command to generate the revert script is
`mybooboo.sh /home/user/mariadb-bin.000001 607 > revert.sql`

If the bad update was a part of larger transaction, you may have other commands between the Table_map and Update_rows records. 
MyBooBoo will attempt to revert all updates from the specified position to the transaction commit record (`Xid`, `end_log_pos 759` in the example) command.

# DO NOT RUN THE REVERT SCRIPT DIRECTLY ON THE MASTER DATABASE!

Due to the limited information contained in the binlog file, the revert script is very convoluted and hard to read and verify.
You should create a new temporary working database, copy the affected to table(s) to it, and run the revert sql script on the working database.

If everything went well, you should have a table in the working database , that has the bad update reverted.

I suggest that you rename this table, copy it back to the master DB, and use a selective update command to fix the original table.

### Limitations
The basic assumption is that the binlog file always contains the 'before' value of all changed rows. In my test cases this was true, but is not guaranteed. If the update command in your binlog does not have the original values, then this tool is useless to you.

The binlog file does not contain the field names, only the positions, which cannot be used directly in the statements. The workaround includes reading the mysql_information schema, and loading this data into variables, then using string manipulation and prepared statements to build the update commands. It could be possible to connecto to the DB directly, and get the field names from the DB, but that would mean pulling in the jdbc connector, and DB access configuration, which I deemed to be too much complexity for limited gains.

