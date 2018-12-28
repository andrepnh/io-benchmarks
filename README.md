## io-calls-benchmarking
JMH benchmarks to compare IO calls

#### Sample results
macOS, SSD and wi-fi:

| Benchmark                                            | Mode | Cnt | Score          | Error          | Units |
|------------------------------------------------------|------|-----|----------------|----------------|-------|
| Empty.baseline                                       | ss   | 100 |       1317.350 | ±      141.958 | ns/op |
| InProcess.localCall                                  | ss   | 100 |       5410.090 | ±      363.582 | ns/op |
| FileSystem.fileRead                                  | ss   | 100 |      23395.920 | ±     1148.550 | ns/op |
| FileSystem.fileReadWithStreamCreationAndDisposal     | ss   | 100 |      88773.560 | ±    47170.658 | ns/op |
| FileSystem.fileWrite                                 | ss   | 100 |     102330.180 | ±    48879.162 | ns/op |
| FileSystem.fileWriteWithStreamCreationAndDisposal    | ss   | 100 |     127015.930 | ±     9696.070 | ns/op |
| Tcp.tcpWrite                                         | ss   | 100 |     112983.220 | ±   242582.785 | ns/op |
| Tcp.tcpWriteWithSocketCreationAndDisposal            | ss   | 100 |     562859.140 | ±   467275.001 | ns/op |
| Tcp.tcpWriteWithStreamCreationAndDisposal            | ss   | 100 |     353692.600 | ±   428789.819 | ns/op |
| Http.localHttpRequest                                | ss   | 100 |    2786269.800 | ±   264926.165 | ns/op |
| Http.remoteHttpRequest                               | ss   | 100 | 2142601262.760 | ± 62227208.586 | ns/op |
| Http.remoteHttpsRequest                              | ss   | 100 | 1818351967.340 | ± 31763625.840 | ns/op |