# jrdiff
Pure java based implementation of rdiff algorithm(with modifications) for creating signatures, deltas for arbitrary binary files

*This library is NOT binary compatible with librsync.*

Modifications to original rdiff algorithm:
1. Use MD5 checksum instead of MD4
2. Support for files >2GB
