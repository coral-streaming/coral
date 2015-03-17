create keyspace testkeyspace with replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };

use keyspace testkeyspace;

create table testkeyspace.test1 (col1 text, col2 int, col3 float, primary key (col1));
create table testkeyspace.test2 (data1 int, data2 text, data3 float, primary key (data2));

insert into testkeyspace.test1 (col1, col2, col3) values ('blabla', 10, 34.1984);
insert into testkeyspace.test1 (col1, col2, col3) values ('abc', 42, 57.661);
insert into testkeyspace.test1 (col1, col2, col3) values ('stuff', 48, 25.63);

insert into testkeyspace.test2 (data1, data2, data3) values (53, 'foo', 18.35);
insert into testkeyspace.test2 (data1, data2, data3) values (72, 'bar', 96.625);