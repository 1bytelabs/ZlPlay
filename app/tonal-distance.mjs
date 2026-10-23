export function shortestSemitoneDelta(from, to) {
  return (((to - from + 6) % 12) + 12) % 12 - 6;
}
