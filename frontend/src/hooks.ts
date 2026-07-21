import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { fetchNeeds, fetchZones } from './api'

export function useChapterSlug(): string {
  const { chapterSlug } = useParams<{ chapterSlug: string }>()
  if (!chapterSlug) throw new Error('Chapter slug required')
  return chapterSlug
}

export function useZones() {
  const chapterSlug = useChapterSlug()
  return useQuery({
    queryKey: ['zones', chapterSlug],
    queryFn: () => fetchZones(chapterSlug),
    staleTime: 5 * 60_000,
  })
}

export function useNeeds(zone?: number, category?: string) {
  const chapterSlug = useChapterSlug()
  return useQuery({
    queryKey: ['needs', chapterSlug, zone, category],
    queryFn: () => fetchNeeds(chapterSlug, zone, category),
    refetchInterval: 30_000,
  })
}
