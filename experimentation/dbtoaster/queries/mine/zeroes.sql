SELECT 1 AS A WHERE (2 > 3);

SELECT 1 AS B
  FROM (SELECT 1 AS A) S
  WHERE A = 2;

SELECT 1 AS C 
  FROM (SELECT 1 AS A) S
  WHERE A IN (SELECT 2);
