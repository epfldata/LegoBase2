CREATE STREAM R(A int, B int) 
  FROM FILE '../../experiments/data/simple/tiny/r.dat' LINE DELIMITED csv;

SELECT total.YER, SUM(CASE total.NAME WHEN 3 THEN total.VOLUME ELSE 0 END) / 
         LISTMAX(1, SUM(total.VOLUME)) AS mkt_share_1
FROM (
  SELECT R.A as YER, R.B as NAME, R.B AS VOLUME
  FROM R
) total
GROUP BY total.YER;

SELECT R.A, SUM(CASE R.B WHEN 3 THEN R.B ELSE 0 END) / 
         LISTMAX(1, SUM(R.B)) AS mkt_share_2
FROM R
GROUP BY R.A;
