import { useQuery } from '@tanstack/react-query'
import { fetchNeeds, fetchZones } from './api'

export function useZones() {
  return useQuery({ queryKey: ['zones'], queryFn: fetchZones, staleTime: 5 * 60_000 })
}

export function useNeeds(zone?: number, category?: string) {
  return useQuery({
    queryKey: ['needs', zone, category],
    queryFn: () => fetchNeeds(zone, category),
    refetchInterval: 30_000,
  })
}
