'use client'

import { useRef, useState, useEffect, useImperativeHandle, forwardRef } from 'react'
import styles from './CarrosselSuave.module.css'

export interface CarrosselSuaveRef {
  rolar: (direcao: 'esq' | 'dir') => void
  rolarParaInicio: () => void
}

interface Props {
  children: React.ReactNode
  className?: string
  deslocamento?: number
  onEstadoScrollChange?: (estado: { noInicio: boolean; noFim: boolean }) => void
}

export const CarrosselSuave = forwardRef<CarrosselSuaveRef, Props>(
  ({ children, className, deslocamento = 320, onEstadoScrollChange }, ref) => {
    const containerRef = useRef<HTMLDivElement>(null)
    const isDownRef = useRef(false)
    const startXRef = useRef(0)
    const scrollLeftRef = useRef(0)
    const lastXRef = useRef(0)
    const lastTimeRef = useRef(0)
    const velocityRef = useRef(0)
    const animIdRef = useRef<number | null>(null)
    const draggedRef = useRef(false)

    const [arrastando, setArrastando] = useState(false)

    const estadoAnteriorRef = useRef({ noInicio: true, noFim: false })

    const verificarLimites = () => {
      const el = containerRef.current
      if (!el || !onEstadoScrollChange) return
      const noInicio = el.scrollLeft <= 5
      const noFim = el.scrollLeft + el.clientWidth >= el.scrollWidth - 5

      if (
        estadoAnteriorRef.current.noInicio !== noInicio ||
        estadoAnteriorRef.current.noFim !== noFim
      ) {
        estadoAnteriorRef.current = { noInicio, noFim }
        onEstadoScrollChange({ noInicio, noFim })
      }
    }

    useEffect(() => {
      const el = containerRef.current
      if (!el) return
      verificarLimites()
      el.addEventListener('scroll', verificarLimites, { passive: true })
      window.addEventListener('resize', verificarLimites)
      return () => {
        el.removeEventListener('scroll', verificarLimites)
        window.removeEventListener('resize', verificarLimites)
      }
    }, [children])

    const rolar = (direcao: 'esq' | 'dir') => {
      if (!containerRef.current) return
      const delta = direcao === 'esq' ? -deslocamento : deslocamento
      containerRef.current.scrollBy({
        left: delta,
        behavior: 'smooth',
      })
    }

    const rolarParaInicio = () => {
      if (!containerRef.current) return
      containerRef.current.scrollTo({
        left: 0,
        behavior: 'smooth',
      })
    }

    useImperativeHandle(ref, () => ({ rolar, rolarParaInicio }))

    const arrastarMouseDown = (e: React.MouseEvent) => {
      if (!containerRef.current) return
      if (animIdRef.current) cancelAnimationFrame(animIdRef.current)

      isDownRef.current = true
      draggedRef.current = false
      setArrastando(true)

      startXRef.current = e.pageX - containerRef.current.offsetLeft
      scrollLeftRef.current = containerRef.current.scrollLeft
      lastXRef.current = e.pageX
      lastTimeRef.current = performance.now()
      velocityRef.current = 0
    }

    const arrastarMouseMove = (e: React.MouseEvent) => {
      if (!isDownRef.current || !containerRef.current) return

      const x = e.pageX - containerRef.current.offsetLeft
      const walk = (x - startXRef.current) * 1.0
      const dist = Math.abs(x - (startXRef.current + containerRef.current.offsetLeft))
      if (dist > 5) draggedRef.current = true

      const now = performance.now()
      const dt = now - lastTimeRef.current
      if (dt > 0) {
        velocityRef.current = (e.pageX - lastXRef.current) / dt
      }
      lastXRef.current = e.pageX
      lastTimeRef.current = now

      containerRef.current.scrollLeft = scrollLeftRef.current - walk
    }

    const arrastarMouseUp = () => {
      if (!isDownRef.current) return
      isDownRef.current = false
      setArrastando(false)

      if (Math.abs(velocityRef.current) > 0.1 && containerRef.current) {
        let v = velocityRef.current * 8
        const deslizar = () => {
          if (!containerRef.current || Math.abs(v) < 0.3) return
          containerRef.current.scrollLeft -= v
          v *= 0.94
          animIdRef.current = requestAnimationFrame(deslizar)
        }
        animIdRef.current = requestAnimationFrame(deslizar)
      }
    }

    const handleClickCapture = (e: React.MouseEvent) => {
      if (draggedRef.current) {
        e.stopPropagation()
        e.preventDefault()
      }
    }

    return (
      <div
        ref={containerRef}
        className={`${styles.container} ${arrastando ? styles.arrastando : ''} ${className ?? ''}`}
        onMouseDown={arrastarMouseDown}
        onMouseMove={arrastarMouseMove}
        onMouseUp={arrastarMouseUp}
        onMouseLeave={arrastarMouseUp}
        onClickCapture={handleClickCapture}
      >
        {children}
      </div>
    )
  },
)

CarrosselSuave.displayName = 'CarrosselSuave'
