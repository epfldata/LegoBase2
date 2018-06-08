--fields is a list in ocaml but an expression in scala

CREATE STREAM R(A int, B int) 
  FROM FILE '../../experiments/data/simple/tiny/r.dat' LINE DELIMITED csv;

SELECT * FROM R;
SELECT COUNT(*) FROM R;
SELECT COUNT(DISTINCT *) FROM R;
SELECT COUNT(DISTINCT B) FROM R;
SELECT COUNT(DISTINCT B) FROM R GROUP BY A;
