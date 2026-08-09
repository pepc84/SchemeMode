(import (processing))

(define (setup) (size 800 600))

(define (draw)
  (background 30)
  (fill 255 100 0)
  (no-stroke)
  (ellipse mouse-x mouse-y 60 60))
