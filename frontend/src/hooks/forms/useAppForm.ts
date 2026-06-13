import {
  useForm,
  useWatch,
  type DefaultValues,
  type FieldValues,
  type Path,
  type UseFormProps,
} from 'react-hook-form'

interface UseAppFormParams<T extends FieldValues> {
  resolver: UseFormProps<T>['resolver']
  defaultValues: DefaultValues<T>
  // campos de texto que precisam estar preenchidos
  requiredFields: Path<T>[]
  // campos booleanos que precisam ser true (ex.: aceite de termos)
  requiredChecks?: Path<T>[]
}

export function useAppForm<T extends FieldValues>({
  resolver,
  defaultValues,
  requiredFields,
  requiredChecks = [],
}: UseAppFormParams<T>) {
  const form = useForm<T>({
    resolver,
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues,
  })

  const allWatchedNames = [...requiredFields, ...requiredChecks]

  const watchedValues = useWatch({
    control: form.control,
    name: allWatchedNames,
  })

  // separa os valores observados em texto e checkbox
  const textValues = watchedValues.slice(0, requiredFields.length)
  const checkValues = watchedValues.slice(requiredFields.length)

  const hasEmptyField = textValues.some(
    (value) => typeof value !== 'string' || value.trim().length === 0,
  )

  const hasUncheckedRequired = checkValues.some((value) => value !== true)

  const isFormIncomplete = hasEmptyField || hasUncheckedRequired

  return { ...form, isFormIncomplete }
}