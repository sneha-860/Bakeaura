import { CheckCircle2, Eye, EyeOff, Store, UserPlus, WandSparkles, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
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
          <button type="button" onClick={() => setShow((value) => !value)} aria-label={show ? 'Hide password' : 'Show password'}>{show ? <EyeOff /> : <Eye />}</button>
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
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState(null);
  const { register, handleSubmit, formState: { errors } } = useForm({ resolver: zodResolver(registerSchema) });

  if (isAuthenticated) return <Navigate to={dashboardForRole(role)} replace />;

  async function submit(values) {
    setLoading(true);
    try {
      const data = await authApi.register({ name: values.name, email: values.email, password: values.password });
      setAuth(data);
      setRegisteredEmail(values.email);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  }

  if (registeredEmail) {
    return (
      <AuthShell title="Check your inbox" subtitle="One step left — verify your email to activate your account.">
        <div className="form-card center">
          <CheckCircle2 size={40} color="var(--sienna)" />
          <p>We sent a verification link to <strong>{registeredEmail}</strong>. Click it to activate your account.</p>
          <p className="muted" style={{ fontSize: '0.88rem' }}>Can't find it? Check your spam folder. The link expires in 24 hours.</p>
          <Button onClick={() => navigate('/')}>Continue to Bakeaura</Button>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Create your Bakeaura account" subtitle="Start as a customer, then apply to sell or influence.">
      <form className="form-card register-form" onSubmit={handleSubmit(submit)}>
        <div className="account-path">
          <div className="path-step active"><UserPlus size={18} /><strong>Customer account</strong><span>Created immediately</span></div>
          <div className="path-step" aria-disabled="true"><Store size={18} /><strong>Seller</strong><span>Apply after signup</span></div>
          <div className="path-step" aria-disabled="true"><WandSparkles size={18} /><strong>Influencer</strong><span>Apply after signup</span></div>
        </div>
        <p className="note">All new accounts start as customers. Seller and influencer access is granted after an application is approved.</p>
        <Input label="Name" error={errors.name?.message} {...register('name')} />
        <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <div className="password-row">
          <Input label="Password" type={showPassword ? 'text' : 'password'} error={errors.password?.message} {...register('password')} />
          <button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff /> : <Eye />}</button>
        </div>
        <div className="password-row">
          <Input label="Confirm password" type={showConfirmPassword ? 'text' : 'password'} error={errors.confirmPassword?.message} {...register('confirmPassword')} />
          <button type="button" onClick={() => setShowConfirmPassword((value) => !value)} aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}>{showConfirmPassword ? <EyeOff /> : <Eye />}</button>
        </div>
        <label className="check-row"><input type="checkbox" {...register('terms')} /> I agree to the Bakeaura terms</label>
        {errors.terms ? <small className="field-error">{errors.terms.message}</small> : null}
        <Button loading={loading}>Register</Button>
        <p className="muted center">Already registered? <Link to="/login">Login</Link></p>
      </form>
    </AuthShell>
  );
}

function VerificationLanding({ title, subtitle, verifyFn, successMessage, successTo, successLabel, onSuccess, showResend }) {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState('verifying');
  const [message, setMessage] = useState('');
  const [resendEmail, setResendEmail] = useState('');
  const [resendStatus, setResendStatus] = useState('idle');
  const [resendMessage, setResendMessage] = useState('');

  async function handleResend(e) {
    e.preventDefault();
    if (!resendEmail) return;
    setResendStatus('loading');
    setResendMessage('');
    try {
      await authApi.resendVerification(resendEmail);
      setResendStatus('sent');
      setResendMessage('If this email exists and is unverified, a new link has been sent.');
    } catch {
      setResendStatus('failed');
      setResendMessage('Something went wrong. Please try again.');
    }
  }

  useEffect(() => {
    const token = searchParams.get('token');
    if (!token) {
      setStatus('error');
      setMessage('This link is missing its verification token.');
      return;
    }
    verifyFn(token)
      .then(() => {
        onSuccess?.();
        setStatus('success');
      })
      .catch((error) => {
        setStatus('error');
        setMessage(error?.response?.data?.message || 'This link is invalid or has expired.');
      });
  }, [searchParams, verifyFn]);

  return (
    <AuthShell title={title} subtitle={subtitle}>
      <div className="form-card center">
        {status === 'verifying' ? <p>Verifying…</p> : null}
        {status === 'success' ? (
          <>
            <CheckCircle2 size={40} />
            <p>{successMessage}</p>
            <Link className="btn btn-primary" to={successTo}>{successLabel}</Link>
          </>
        ) : null}
        {status === 'error' ? (
          <>
            <XCircle size={40} />
            <p className="field-error">{message}</p>
            {showResend && resendStatus !== 'sent' ? (
              <form onSubmit={handleResend} style={{ width: '100%', marginTop: '1rem' }}>
                <p className="muted" style={{ marginBottom: '0.5rem' }}>Need a new link?</p>
                <Input
                  label="Your email address"
                  type="email"
                  value={resendEmail}
                  onChange={(e) => setResendEmail(e.target.value)}
                  required
                />
                <Button loading={resendStatus === 'loading'} style={{ marginTop: '0.5rem' }}>
                  Resend verification email
                </Button>
                {resendStatus === 'failed' ? (
                  <p className="field-error" style={{ marginTop: '0.5rem' }}>{resendMessage}</p>
                ) : null}
              </form>
            ) : null}
            {showResend && resendStatus === 'sent' ? (
              <p className="muted" style={{ marginTop: '1rem' }}>{resendMessage}</p>
            ) : null}
            <Link className="btn btn-ghost" to="/">Back to home</Link>
          </>
        ) : null}
      </div>
    </AuthShell>
  );
}

export function VerifyEmailPage() {
  const { isAuthenticated, emailVerified, setEmailVerified } = useAuthStore();

  if (isAuthenticated && emailVerified) {
    return (
      <AuthShell title="Email verification" subtitle="Confirming your email address.">
        <div className="form-card center">
          <CheckCircle2 size={40} />
          <p>Your email is already verified.</p>
          <Link className="btn btn-primary" to="/">Go to Bakeaura</Link>
        </div>
      </AuthShell>
    );
  }

  return (
    <VerificationLanding
      title="Email verification"
      subtitle="Confirming your email address."
      verifyFn={authApi.verifyEmail}
      successMessage="Your email is verified. You can now place orders and apply for roles."
      successTo="/"
      successLabel="Go to Bakeaura"
      onSuccess={setEmailVerified}
      showResend
    />
  );
}

export function VerifyEmailChangePage() {
  const { logout } = useAuthStore();
  return (
    <VerificationLanding
      title="Confirm new email"
      subtitle="Confirming your updated email address."
      verifyFn={authApi.verifyEmailChange}
      successMessage="Your email has been updated. Please log in again with your new email."
      successTo="/login"
      successLabel="Go to login"
      onSuccess={logout}
    />
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
