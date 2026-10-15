# ShitTok orientation fixtures

These original, single-frame black H.264 MP4s exercise Media3 container metadata without a decoder or network. Each file is about 1.5 KiB and keeps the moov atom after media data.

| File | Encoded size | Pixel ratio | Display rotation | Effective aspect |
| --- | --- | --- | --- | --- |
| portrait.mp4 | 18 x 32 | 1 | 0 | 0.5625 |
| square.mp4 | 32 x 32 | 1 | 0 | 1 |
| landscape.mp4 | 32 x 18 | 1 | 0 | 1.7778 |
| wide-pixels.mp4 | 18 x 32 | 2 | 0 | 1.125 |
| rotated.mp4 | 32 x 18 | 1 | 90 | 0.5625 |

Generate the unrotated files with FFmpeg, substituting the table's size and pixel ratio:

```sh
ffmpeg -f lavfi -i color=black:s=18x32:r=5 -frames:v 1 -vf setsar=1 -c:v libx264 -pix_fmt yuv420p portrait.mp4
```

Create the rotation fixture with an actual display matrix:

```sh
ffmpeg -display_rotation 90 -i landscape.mp4 -c copy rotated.mp4
ffprobe -v error -select_streams v:0 -show_entries stream=width,height,sample_aspect_ratio:stream_side_data=rotation -of json rotated.mp4
```
