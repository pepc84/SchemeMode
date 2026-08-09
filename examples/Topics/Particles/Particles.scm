(import (processing))

(define ps (list))

(define (make-p x y)
  (list x y (- (random -3.0 3.0)) (random -4.0 -0.5) (random 40.0 80.0)))

(define (p-x p) (list-ref p 0)) (define (p-y p) (list-ref p 1))
(define (p-vx p) (list-ref p 2)) (define (p-vy p) (list-ref p 3))
(define (p-life p) (list-ref p 4))
(define (p-alive? p) (> (p-life p) 0))
(define (p-update p)
  (list (+ (p-x p)(p-vx p)) (+ (p-y p)(p-vy p) 0.1)
        (p-vx p) (+ (p-vy p) 0.12) (- (p-life p) 1)))
(define (p-draw p)
  (let ((a (* (/ (p-life p) 80.0) 220)))
    (fill 255 160 60 a) (no-stroke) (circle (p-x p) (p-y p) 8)))

(define (setup) (size 800 600))

(define (draw)
  (background 10 10 20 30)
  (set! ps (cons (make-p mouse-x mouse-y) ps))
  (set! ps (filter p-alive? (map p-update ps)))
  (for-each p-draw ps))

(define (mouse-pressed)
  (let loop ((i 0))
    (when (< i 15)
      (set! ps (cons (make-p mouse-x mouse-y) ps))
      (loop (+ i 1)))))
