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
  const { isAuthenticated, emailVerified, role, setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState(null);
  const [resendStatus, setResendStatus] = useState('idle');
  const { register, handleSubmit, formState: { errors } } = useForm({ resolver: zodResolver(registerSchema) });

  // Only redirect if authenticated AND verified — not right after registration where
  // emailVerified is false and we need to show the "Check your inbox" screen.
  if (isAuthenticated && emailVerified) return <Navigate to={dashboardForRole(role)} replace />;

  async function submit(values) {
    setLoading(true);
    try {
      const data = await authApi.register({ name: values.name, email: values.email, password: values.password });
      setRegisteredEmail(values.email);
      setAuth(data);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    if (!registeredEmail || resendStatus === 'loading' || resendStatus === 'sent') return;
    setResendStatus('loading');
    try {
      await authApi.resendVerification(registeredEmail);
      setResendStatus('sent');
    } catch {
      setResendStatus('idle');
      toast.error('Could not resend. Please try again.');
    }
  }

  if (registeredEmail) {
    return (
      <AuthShell title="Check your inbox" subtitle="One step left — verify your email to activate your account.">
        <div className="form-card center">
          <CheckCircle2 size={40} color="var(--sienna)" />
          <p>We sent a verification link to <strong>{registeredEmail}</strong>. Click it to activate your account.</p>
          <p className="muted" style={{ fontSize: '0.88rem' }}>Can't find it? Check your spam folder. The link expires in 24 hours.</p>
          {resendStatus === 'sent' ? (
            <p className="muted" style={{ fontSize: '0.88rem', color: 'var(--sage)' }}>New link sent — check your inbox and spam folder.</p>
          ) : (
            <Button variant="ghost" loading={resendStatus === 'loading'} onClick={handleResend} style={{ fontSize: '0.88rem' }}>
              Resend verification email
            </Button>
          )}
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

function VerifyShell({ children }) {
  return (
    <div className="verify-shell">
      <div className="verify-card">
        <Link to="/" className="verify-brand">
          <span className="brand-mark">B</span>
          <span>Bakeaura</span>
        </Link>
        {children}
      </div>
    </div>
  );
}

function VerificationLanding({ successMessage, successTo, successLabel, onSuccess, showResend }) {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState('verifying');
  const [message, setMessage] = useState('');
  const [resendEmail, setResendEmail] = useState('');
  const [resendStatus, setResendStatus] = useState('idle');
  const [resendMessage, setResendMessage] = useState('');

  const errorMessages = {
    expired: 'This verification link has expired. Use the form below to request a new one.',
    invalid: 'This link is invalid or has already been used. Request a new one below.',
  };

  async function handleResend(e) {
    e.preventDefault();
    if (!resendEmail) return;
    setResendStatus('loading');
    setResendMessage('');
    try {
      await authApi.resendVerification(resendEmail);
      setResendStatus('sent');
      setResendMessage('If this email exists and is unverified, a new link has been sent. Check your inbox and spam folder.');
    } catch {
      setResendStatus('failed');
      setResendMessage('Something went wrong. Please try again.');
    }
  }

  // The backend verifies the token and redirects here with ?verified=true or ?error=<code>.
  // No API call needed — just read the redirect params.
  useEffect(() => {
    const verified = searchParams.get('verified');
    const error = searchParams.get('error');

    if (verified === 'true') {
      onSuccess?.();
      setStatus('success');
    } else if (error) {
      setStatus('error');
      setMessage(errorMessages[error] || 'Something went wrong. Request a new link below.');
    } else {
      setStatus('error');
      setMessage('No verification link detected. Request a new one below.');
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <VerifyShell>
      {status === 'verifying' && (
        <div className="verify-state">
          <div className="verify-spinner-ring" />
          <h2>Verifying your email</h2>
          <p>Just a moment while we confirm your address…</p>
        </div>
      )}

      {status === 'success' && (
        <div className="verify-state">
          <div className="verify-icon-wrap verify-icon-success">
            <CheckCircle2 size={36} />
          </div>
          <h2>Email verified!</h2>
          <p>{successMessage}</p>
          <Link className="btn btn-primary" to={successTo} style={{ marginTop: '12px', width: '100%', justifyContent: 'center' }}>
            {successLabel}
          </Link>
        </div>
      )}

      {status === 'error' && (
        <div className="verify-state">
          <div className="verify-icon-wrap verify-icon-error">
            <XCircle size={36} />
          </div>
          <h2>Verification failed</h2>
          <p className="verify-error-msg">{message}</p>

          {showResend && resendStatus !== 'sent' && (
            <form className="verify-resend-form" onSubmit={handleResend}>
              <p className="verify-resend-label">Request a new link</p>
              <Input
                label="Your email address"
                type="email"
                value={resendEmail}
                onChange={(e) => setResendEmail(e.target.value)}
                required
              />
              <Button loading={resendStatus === 'loading'}>Resend verification email</Button>
              {resendStatus === 'failed' && <p className="field-error">{resendMessage}</p>}
            </form>
          )}

          {showResend && resendStatus === 'sent' && (
            <div className="verify-sent-notice">
              <CheckCircle2 size={18} />
              <p>{resendMessage}</p>
            </div>
          )}

          <div className="verify-divider" />
          <Link to="/" className="btn btn-ghost" style={{ width: '100%', justifyContent: 'center' }}>Back to home</Link>
        </div>
      )}
    </VerifyShell>
  );
}

export function VerifyEmailPage() {
  const { isAuthenticated, emailVerified, setEmailVerified } = useAuthStore();

  if (isAuthenticated && emailVerified) {
    return (
      <VerifyShell>
        <div className="verify-state">
          <div className="verify-icon-wrap verify-icon-success">
            <CheckCircle2 size={36} />
          </div>
          <h2>Already verified</h2>
          <p>Your email address is already confirmed — you are all set.</p>
          <Link className="btn btn-primary" to="/" style={{ marginTop: '12px', width: '100%', justifyContent: 'center' }}>
            Go to Bakeaura
          </Link>
        </div>
      </VerifyShell>
    );
  }

  return (
    <VerificationLanding
      successMessage="Your email is verified. You can now place orders and apply for roles."
      successTo="/"
      successLabel="Start exploring"
      onSuccess={setEmailVerified}
      showResend
    />
  );
}

export function VerifyEmailChangePage() {
  const { logout } = useAuthStore();
  return (
    <VerificationLanding
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
