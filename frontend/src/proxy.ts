import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const PUBLIC_ROUTES = ['/', '/login', '/cadastro', '/esqueci-senha', '/redefinir-senha']

const HOME_ROUTE = '/inicio'

function matchesRoute(pathname: string, route: string) {
  if (route === '/') return pathname === '/'
  return pathname === route || pathname.startsWith(`${route}/`)
}

export function proxy(request: NextRequest) {
  const token = request.cookies.get('domus:token')?.value
  const { pathname, search } = request.nextUrl

  const isPublicRoute = PUBLIC_ROUTES.some((route) => matchesRoute(pathname, route))

  if (!token && !isPublicRoute) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('next', `${pathname}${search}`)
    return NextResponse.redirect(loginUrl)
  }

  if (token && isPublicRoute) {
    return NextResponse.redirect(new URL(HOME_ROUTE, request.url))
  }

  return NextResponse.next()
}

export const config = {
  matcher: [
    '/((?!api|_next/static|_next/image|images|favicon.ico).*)',
  ],
}
