import { forwardRef } from 'react';

const Input = forwardRef(function Input({ label, error, className = '', as = 'input', ...props }, ref) {
  const Component = as;
  return (
    <label className={`field ${className}`}>
      {label ? <span>{label}</span> : null}
      <Component ref={ref} className="input" {...props} />
      {error ? <small className="field-error">{error}</small> : null}
    </label>
  );
});

export default Input;
