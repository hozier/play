# play
In development: Various implementations of puzzle solving algorithms<br><br>

Backtracking algorithm runtime:

[success] Total time: 461 s, completed May 12, 2016 11:55:41 PM

current trace [20 9x9, 1 5x5, 1 4x4]

Avg runtime/board ~23 s

--

update

[success] Total time: 15 s, completed May 14, 2016 3:10:04 AM

Avg runtime/board ~1.4 s

# run
To run: use the `sbt run` command on the cli from root directory
# analytics
`. 1 . . 2 . 3 . .`<br>
`. . 2 . . 3 . 4 .` <br>
`. 5 . . . . . . 6` <br>
`. . 7 8 . . . 5 .` <br>
`. . . 1 . . . . 4`<br>
`. 8 . . 9 4 . . .` <br>
`3 . . . . 7 . 9 .` <br>
`. . . 4 . . 1 . 5` <br>
`. . 6 . . . . . .` <br>


`6 1 4 5 2 9 3 8 7` <br>
`8 7 2 6 1 3 5 4 9` <br>
`9 5 3 7 4 8 2 1 6` <br>
`4 3 7 8 6 2 9 5 1` <br>
`2 6 9 1 7 5 8 3 4` <br>
`1 8 5 3 9 4 7 6 2` <br>
`3 4 1 2 5 7 6 9 8` <br>
`7 9 8 4 3 6 1 2 5` <br>
`5 2 6 9 8 1 4 7 3` <br>


Algorithm:<br>
backtracking<br>
Elapsed:<br>
[0.316678s]<br><br><br>




`. 1 . . 2`<br>
`. 3 . . .`<br>
`. 4 . . 3`<br>
`. 5 . . 1`<br>
`. 2 . . .`<br>

`4 1 3 5 2`<br>
`5 3 1 2 4`<br>
`2 4 5 1 3`<br>
`3 5 2 4 1`<br>
`1 2 4 3 5`<br>

Algorithm:<br>
backtracking<br>
Elapsed:<br>
[0.002222s]<br><br><br>




`4 . 1 .`<br>
`1 2 . .`<br>
`. 1 . .`<br>
`. 4 . 1`<br>

`4 3 1 2`<br>
`1 2 3 4`<br>
`2 1 4 3`<br>
`3 4 2 1`<br>

Algorithm:<br>
backtracking<br>
Elapsed:<br>
[0.000629s]
