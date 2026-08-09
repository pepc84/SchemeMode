(import (processing))

(define angle 0.0)

(define (setup)
  (size 800 600)
  (mode-3d))

(define (draw)
  (background 20)
  (set! angle (+ angle 0.01))
  (push-matrix)
  (rotate-x angle)
  (rotate-y (* angle 1.3))
  (rotate-z (* angle 0.7))
  (fill 100 180 255 200)
  (stroke 255 255 255 80)
  (box 150 150 150)
  (pop-matrix))
