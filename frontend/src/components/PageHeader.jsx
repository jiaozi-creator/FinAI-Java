function PageHeader({ kicker, title, description, extra }) {
  return (
    <header className="page-head">
      <div>
        {kicker && <div className="kicker">{kicker}</div>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {extra && <div>{extra}</div>}
    </header>
  )
}

export default PageHeader
