import { useRef, useState } from 'react'

export function useArrastarParaRolar<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [arrastando, setArrastando] = useState(false)
  const [startX, setStartX] = useState(0)
  const [scrollLeft, setScrollLeft] = useState(0)

  const handleMouseDown = (e: React.MouseEvent) => {
    if (!ref.current) return
    setArrastando(true)
    setStartX(e.pageX - ref.current.offsetLeft)
    setScrollLeft(ref.current.scrollLeft)
  }

  const handleMouseLeave = () => {
    setArrastando(false)
  }

  const handleMouseUp = () => {
    setArrastando(false)
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!arrastando || !ref.current) return
    e.preventDefault()
    const x = e.pageX - ref.current.offsetLeft
    const deslocamento = (x - startX) * 1.5
    ref.current.scrollLeft = scrollLeft - deslocamento
  }

  return {
    ref,
    propsArrasto: {
      onMouseDown: handleMouseDown,
      onMouseLeave: handleMouseLeave,
      onMouseUp: handleMouseUp,
      onMouseMove: handleMouseMove,
    },
  }
}
