(import (processing))

(define x 300.0) (define y 200.0)
(define vx 3.5)  (define vy 2.2)
(define r 25.0)

(define (setup) (size 600 400))

(define (draw)
  (background 20)
  (set! x (+ x vx)) (set! y (+ y vy))
  (when (or (> (+ x r) width)  (< (- x r) 0)) (set! vx (- vx)))
  (when (or (> (+ y r) height) (< (- y r) 0)) (set! vy (- vy)))
  (fill 80 180 255) (no-stroke)
  (ellipse x y (* 2 r) (* 2 r)))
