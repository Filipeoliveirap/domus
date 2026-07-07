import {
  useForm,
  useWatch,
  type DefaultValues,
  type FieldValues,
  type Path,
  type Resolver,
} from 'react-hook-form'

interface UseAppFormParams<TInput extends FieldValues, TOutput extends FieldValues> {
  resolver: Resolver<TInput, unknown, TOutput>
  defaultValues: DefaultValues<TInput>
  requiredFields: Path<TInput>[]
  requiredChecks?: Path<TInput>[]
}

export function useAppForm<
  TInput extends FieldValues,
  TOutput extends FieldValues = TInput,
>({
  resolver,
  defaultValues,
  requiredFields,
  requiredChecks = [],
}: UseAppFormParams<TInput, TOutput>) {
  const form = useForm<TInput, unknown, TOutput>({
    resolver,
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues,
  })

  const allWatchedNames = [...requiredFields, ...requiredChecks]
  
  const watched = useWatch({
    control: form.control,
    name: allWatchedNames,
  })

  const valores = allWatchedNames.map((name, i) => watched?.[i] ?? form.getValues(name))

  const textValues = valores.slice(0, requiredFields.length)
  const checkValues = valores.slice(requiredFields.length)

  const hasEmptyField = textValues.some(
    (value) => value == null || String(value).trim().length === 0,
  )
  const hasUncheckedRequired = checkValues.some((value) => value !== true)
  const isFormIncomplete = hasEmptyField || hasUncheckedRequired
  
  return { ...form, isFormIncomplete }
}