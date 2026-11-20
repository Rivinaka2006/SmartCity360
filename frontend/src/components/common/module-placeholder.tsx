type ModulePlaceholderProps = {
  title: string
  description: string
}

export function ModulePlaceholder({ title, description }: ModulePlaceholderProps) {
  return (
    <section className="module-placeholder" aria-labelledby="module-title">
      <p className="eyebrow">Foundation ready</p>
      <h1 id="module-title">{title}</h1>
      <p>{description}</p>
      <p className="muted">The business feature interface is planned for a later frontend phase.</p>
    </section>
  )
}
