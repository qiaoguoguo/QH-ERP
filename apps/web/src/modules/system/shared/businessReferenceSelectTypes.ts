export type BusinessReferenceId = string | number

export interface BusinessReferenceOption {
  id: BusinessReferenceId
  label: string
  disabled?: boolean
  disabledReason?: string | null
}
