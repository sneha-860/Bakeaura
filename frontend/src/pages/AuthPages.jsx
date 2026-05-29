import { Eye, EyeOff, Store, UserPlus, WandSparkles } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { authApi } from '../api/auth';
import Button from '../components/Button';
import Input from '../components/Input';
import { dashboardForRole } from '../components/RequireAuth';
import { useAuthStore } from '../store/useAuthStore';

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(6)
});

const registerSchema = z.object({
  name: z.string().min(2),
  email: z.string().email(),
  password: z.string().min(6),
  confirmPassword: z.string().min(6),
  terms: z.literal(true, { errorMap: () => ({ message: 'Accept terms to continue' }) })
}).refine((value) => value.password === value.confirmPassword, {
  message: 'Passwords must match',
  path: ['confirmPassword']
});

export function LoginPage() {
  const navigate = useNavigate();
  const { isAuthenticated, role, setAuth } = useAuthStore();
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm({ resolver: zodResolver(loginSchema) });

  if (isAuthenticated) return <Navigate to={dashboardForRole(role)} replace />;

  async function submit(values) {
    setLoading(true);
    try {
      const data = await authApi.login(values);
      setAuth(data);
      toast.success('Welcome back');
      navigate(dashboardForRole(data.role));
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to order from neighborhood bakers.">
      <form className="form-card" onSubmit={handleSubmit(submit)}>
        <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <div className="password-row">
          <Input label="Password" type={show ? 'text' : 'password'} error={errors.password?.message} {...register('password')} />
          <button type="button" onClick={() => setShow((value) => !value)}>{show ? <EyeOff /> : <Eye />}</button>
        </div>
        <Button loading={loading}>Login</Button>
        <p className="muted center">New to Bakeaura? <Link to="/register">Create an account</Link></p>
      </form>
    </AuthShell>
  );
}

export function RegisterPage() {
  const navigate = useNavigate();
  const { isAuthenticated, role, setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm({ resolver: zodResolver(registerSchema) });

  if (isAuthenticated) return <Navigate to={dashboardForRole(role)} replace />;

  async function submit(values) {
    setLoading(true);
    try {
      const data = await authApi.register({ name: values.name, email: values.email, password: values.password });
      setAuth(data);
      toast.success('Account created');
      navigate('/');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell title="Create your Bakeaura account" subtitle="Start as a customer, then apply to sell or influence.">
      <form className="form-card" onSubmit={handleSubmit(submit)}>
        <div className="account-path">
          <div className="path-step active"><UserPlus size={18} /><strong>Customer account</strong><span>Created immediately</span></div>
          <div className="path-step"><Store size={18} /><strong>Seller</strong><span>Apply after signup</span></div>
          <div className="path-step"><WandSparkles size={18} /><strong>Influencer</strong><span>Apply after signup</span></div>
        </div>
        <p className="note">All new accounts start as customers. Seller and influencer access is granted after an application is approved.</p>
        <Input label="Name" error={errors.name?.message} {...register('name')} />
        <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <Input label="Password" type="password" error={errors.password?.message} {...register('password')} />
        <Input label="Confirm password" type="password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
        <label className="check-row"><input type="checkbox" {...register('terms')} /> I agree to the Bakeaura terms</label>
        {errors.terms ? <small className="field-error">{errors.terms.message}</small> : null}
        <Button loading={loading}>Register</Button>
        <p className="muted center">Already registered? <Link to="/login">Login</Link></p>
      </form>
    </AuthShell>
  );
}

function AuthShell({ title, subtitle, children }) {
  return (
    <section className="auth-page">
      <div className="auth-art"><div><span className="brand-mark">B</span><h1>{title}</h1><p>{subtitle}</p></div></div>
      {children}
    </section>
  );
}
